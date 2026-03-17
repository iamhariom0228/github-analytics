package com.gitanalytics.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitanalytics.ingestion.entity.TrackedRepo;
import com.gitanalytics.ingestion.kafka.SyncProducer;
import com.gitanalytics.ingestion.dao.TrackedRepoDao;
import com.gitanalytics.shared.config.AppProperties;
import com.gitanalytics.shared.exception.UnauthorizedException;
import com.gitanalytics.shared.kafka.events.WebhookReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final TrackedRepoDao trackedRepoDao;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public void processWebhook(String event, String signature, String deliveryId, String payload) {
        validateSignature(payload, signature);

        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode repoNode = node.path("repository");
            if (repoNode.isMissingNode()) return;

            long githubRepoId = repoNode.get("id").asLong();
            // Find the tracked repo across all users
            trackedRepoDao.findAll().stream()
                .filter(r -> r.getGithubRepoId() == githubRepoId)
                .forEach(repo -> publishWebhookEvent(repo, event, deliveryId, payload));

        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
        }
    }

    private void publishWebhookEvent(TrackedRepo repo, String event, String deliveryId, String payload) {
        kafkaTemplate.send("ga.webhook.received",
            repo.getId().toString(),
            new WebhookReceivedEvent(deliveryId, repo.getUser().getId(), repo.getId(), event, payload)
        );
    }

    private void validateSignature(String payload, String signature) {
        String secret = appProperties.getGithub().getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("Webhook secret not configured — skipping signature validation");
            return;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(digest);
            if (!expected.equals(signature)) {
                throw new UnauthorizedException("Invalid webhook signature");
            }
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("Signature validation error");
        }
    }
}
