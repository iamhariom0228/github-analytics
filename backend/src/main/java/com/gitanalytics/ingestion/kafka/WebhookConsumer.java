package com.gitanalytics.ingestion.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitanalytics.ingestion.entity.PullRequest;
import com.gitanalytics.ingestion.entity.PrReview;
import com.gitanalytics.ingestion.repository.PullRequestRepository;
import com.gitanalytics.ingestion.repository.PrReviewRepository;
import com.gitanalytics.ingestion.repository.TrackedRepoRepository;
import com.gitanalytics.shared.kafka.events.SyncCompletedEvent;
import com.gitanalytics.shared.kafka.events.WebhookReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookConsumer {

    private final TrackedRepoRepository trackedRepoRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PrReviewRepository prReviewRepository;
    private final SyncProducer syncProducer;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ga.webhook.received", groupId = "github-analytics")
    @Transactional
    public void handleWebhook(WebhookReceivedEvent event) {
        log.debug("Processing webhook: type={}, repo={}", event.getEventType(), event.getRepoId());
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            switch (event.getEventType()) {
                case "pull_request" -> processPullRequestEvent(event.getRepoId(), payload);
                case "pull_request_review" -> processPullRequestReviewEvent(event.getRepoId(), payload);
                case "push" -> log.debug("Push event recorded for repo {}", event.getRepoId());
            }
            // Trigger cache invalidation
            syncProducer.publishSyncCompleted(new SyncCompletedEvent(
                null, event.getUserId(), List.of(event.getRepoId()), 1
            ));
        } catch (Exception e) {
            log.error("Failed to process webhook event: {}", e.getMessage(), e);
        }
    }

    private void processPullRequestEvent(UUID repoId, JsonNode payload) {
        JsonNode prNode = payload.get("pull_request");
        if (prNode == null) return;

        int number = prNode.get("number").asInt();
        PullRequest pr = pullRequestRepository.findByRepoIdAndPrNumber(repoId, number)
            .orElseGet(() -> {
                var repo = trackedRepoRepository.findById(repoId).orElse(null);
                if (repo == null) return null;
                return PullRequest.builder().repo(repo).prNumber(number).build();
            });

        if (pr == null) return;

        pr.setTitle(prNode.get("title").asText());
        if (prNode.has("user")) pr.setAuthorLogin(prNode.get("user").get("login").asText());

        String state = prNode.get("state").asText();
        boolean merged = !prNode.get("merged_at").isNull();
        pr.setState(merged ? PullRequest.PrState.MERGED : "closed".equals(state)
            ? PullRequest.PrState.CLOSED : PullRequest.PrState.OPEN);

        if (!prNode.get("created_at").isNull())
            pr.setCreatedAt(OffsetDateTime.parse(prNode.get("created_at").asText()));
        if (!prNode.get("merged_at").isNull())
            pr.setMergedAt(OffsetDateTime.parse(prNode.get("merged_at").asText()));
        if (!prNode.get("closed_at").isNull())
            pr.setClosedAt(OffsetDateTime.parse(prNode.get("closed_at").asText()));

        pullRequestRepository.save(pr);
    }

    private void processPullRequestReviewEvent(UUID repoId, JsonNode payload) {
        JsonNode reviewNode = payload.get("review");
        JsonNode prNode = payload.get("pull_request");
        if (reviewNode == null || prNode == null) return;

        int prNumber = prNode.get("number").asInt();
        PullRequest pr = pullRequestRepository.findByRepoIdAndPrNumber(repoId, prNumber).orElse(null);
        if (pr == null) return;

        PrReview.ReviewState reviewState;
        try {
            reviewState = PrReview.ReviewState.valueOf(reviewNode.get("state").asText());
        } catch (Exception e) {
            reviewState = PrReview.ReviewState.COMMENTED;
        }

        String reviewerLogin = reviewNode.get("user").get("login").asText();
        OffsetDateTime submittedAt = OffsetDateTime.parse(reviewNode.get("submitted_at").asText());

        prReviewRepository.save(PrReview.builder()
            .pullRequest(pr)
            .reviewerLogin(reviewerLogin)
            .state(reviewState)
            .submittedAt(submittedAt)
            .build());

        // Update first_review_at
        if (pr.getFirstReviewAt() == null || submittedAt.isBefore(pr.getFirstReviewAt())) {
            pr.setFirstReviewAt(submittedAt);
            pullRequestRepository.save(pr);
        }
    }
}
