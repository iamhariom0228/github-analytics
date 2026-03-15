package com.gitanalytics.ingestion.kafka;

import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.auth.service.GitHubOAuthService;
import com.gitanalytics.ingestion.client.GitHubApiClient;
import com.gitanalytics.ingestion.entity.*;
import com.gitanalytics.ingestion.repository.*;
import com.gitanalytics.shared.exception.GitHubApiException;
import com.gitanalytics.shared.kafka.events.SyncCompletedEvent;
import com.gitanalytics.shared.kafka.events.SyncRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncConsumer {

    private final TrackedRepoRepository trackedRepoRepository;
    private final CommitRepository commitRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PrReviewRepository prReviewRepository;
    private final SyncJobRepository syncJobRepository;
    private final UserRepository userRepository;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubOAuthService gitHubOAuthService;
    private final SyncProducer syncProducer;
    private final RedisTemplate<String, Object> redisTemplate;

    @KafkaListener(topics = "ga.sync.requested", groupId = "github-analytics")
    @Transactional
    public void handleSyncRequested(SyncRequestedEvent event) {
        log.info("Processing sync: jobId={}, repoId={}, type={}", event.getSyncJobId(), event.getRepoId(), event.getSyncType());

        TrackedRepo repo = trackedRepoRepository.findById(event.getRepoId()).orElse(null);
        if (repo == null) {
            log.warn("Repo not found: {}", event.getRepoId());
            return;
        }

        SyncJob job = syncJobRepository.findById(event.getSyncJobId()).orElse(null);
        if (job == null) return;

        // Mark running
        repo.setSyncStatus(TrackedRepo.SyncStatus.SYNCING);
        trackedRepoRepository.save(repo);
        job.setStatus(SyncJob.JobStatus.RUNNING);
        job.setStartedAt(OffsetDateTime.now());
        syncJobRepository.save(job);

        String lockKey = "ga:sync:lock:" + repo.getId();
        try {
            User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
            String accessToken = gitHubOAuthService.decryptAccessToken(user);

            OffsetDateTime since = "FULL_SYNC".equals(event.getSyncType()) ? null : repo.getLastSyncedAt();
            AtomicInteger count = new AtomicInteger(0);

            syncCommits(repo, accessToken, user.getId(), since, count);
            syncPullRequests(repo, accessToken, user.getId(), since, count);

            repo.setSyncStatus(TrackedRepo.SyncStatus.DONE);
            repo.setLastSyncedAt(OffsetDateTime.now());
            trackedRepoRepository.save(repo);

            job.setStatus(SyncJob.JobStatus.DONE);
            job.setCompletedAt(OffsetDateTime.now());
            job.setRecordsProcessed(count.get());
            syncJobRepository.save(job);

            syncProducer.publishSyncCompleted(new SyncCompletedEvent(
                event.getSyncJobId(), event.getUserId(), List.of(event.getRepoId()), count.get()
            ));

        } catch (Exception e) {
            log.error("Sync failed for repo {}: {}", event.getRepoId(), e.getMessage(), e);
            repo.setSyncStatus(TrackedRepo.SyncStatus.FAILED);
            trackedRepoRepository.save(repo);
            job.setStatus(SyncJob.JobStatus.FAILED);
            job.setCompletedAt(OffsetDateTime.now());
            job.setErrorMessage(e.getMessage());
            syncJobRepository.save(job);
        } finally {
            // Always release the lock so re-syncs aren't blocked
            redisTemplate.delete(lockKey);
        }
    }

    private void syncCommits(TrackedRepo repo, String token, UUID userId,
                              OffsetDateTime since, AtomicInteger count) {
        List<GitHubApiClient.GitHubCommitDto> commits = gitHubApiClient.getCommits(
            token, userId, repo.getOwner(), repo.getName(), since);

        for (GitHubApiClient.GitHubCommitDto dto : commits) {
            if (dto.getCommit() == null || dto.getCommit().getAuthor() == null) continue;

            // Fetch individual commit for stats (additions/deletions)
            int additions = 0, deletions = 0;
            String authorLogin = dto.getAuthor() != null ? dto.getAuthor().getLogin() : null;
            Long authorGithubId = dto.getAuthor() != null ? dto.getAuthor().getId() : null;

            GitHubApiClient.GitHubCommitDetailDto detail =
                gitHubApiClient.getCommitDetail(token, userId, repo.getOwner(), repo.getName(), dto.getSha());
            if (detail != null) {
                if (detail.getStats() != null) {
                    additions = detail.getStats().getAdditions();
                    deletions = detail.getStats().getDeletions();
                }
                // Fall back to detail's author if list response had null author
                if (authorLogin == null && detail.getAuthor() != null) {
                    authorLogin = detail.getAuthor().getLogin();
                    authorGithubId = detail.getAuthor().getId();
                }
            }

            // Final fallback: use commit author name if GitHub user still unresolved
            if (authorLogin == null && dto.getCommit().getAuthor() != null) {
                authorLogin = dto.getCommit().getAuthor().getName();
            }

            Commit commit = Commit.builder()
                .repo(repo)
                .sha(dto.getSha())
                .authorLogin(authorLogin)
                .authorGithubId(authorGithubId)
                .messageSummary(firstLine(dto.getCommit().getMessage()))
                .additions(additions)
                .deletions(deletions)
                .committedAt(dto.getCommit().getAuthor().getDate())
                .build();
            try {
                commitRepository.upsert(commit);
                count.incrementAndGet();
            } catch (Exception e) {
                log.debug("Failed to upsert commit {}: {}", dto.getSha(), e.getMessage());
            }
        }
    }

    private void syncPullRequests(TrackedRepo repo, String token, UUID userId,
                                   OffsetDateTime since, AtomicInteger count) {
        List<GitHubApiClient.GitHubPRDto> prs = gitHubApiClient.getPullRequests(
            token, userId, repo.getOwner(), repo.getName(), since);

        for (GitHubApiClient.GitHubPRDto dto : prs) {
            PullRequest.PrState state = switch (dto.getState()) {
                case "closed" -> dto.getMergedAt() != null ? PullRequest.PrState.MERGED : PullRequest.PrState.CLOSED;
                default -> PullRequest.PrState.OPEN;
            };

            PullRequest pr = pullRequestRepository.findByRepoIdAndPrNumber(repo.getId(), dto.getNumber())
                .orElseGet(() -> PullRequest.builder().repo(repo).prNumber(dto.getNumber()).build());

            pr.setTitle(dto.getTitle());
            pr.setAuthorLogin(dto.getUser() != null ? dto.getUser().getLogin() : null);
            pr.setState(state);
            pr.setCreatedAt(dto.getCreatedAt());
            pr.setMergedAt(dto.getMergedAt());
            pr.setClosedAt(dto.getClosedAt());
            pr.setAdditions(dto.getAdditions());
            pr.setDeletions(dto.getDeletions());
            pr.setChangedFiles(dto.getChangedFiles());

            PullRequest savedPr = pullRequestRepository.save(pr);
            count.incrementAndGet();

            // Sync reviews
            syncReviews(savedPr, repo, token, userId);
        }
    }

    private void syncReviews(PullRequest pr, TrackedRepo repo, String token, UUID userId) {
        try {
            List<GitHubApiClient.GitHubReviewDto> reviews = gitHubApiClient.getReviews(
                token, userId, repo.getOwner(), repo.getName(), pr.getPrNumber());

            OffsetDateTime firstReview = null;
            for (GitHubApiClient.GitHubReviewDto dto : reviews) {
                if (dto.getUser() == null || dto.getSubmittedAt() == null) continue;
                PrReview.ReviewState state;
                try {
                    state = PrReview.ReviewState.valueOf(dto.getState());
                } catch (IllegalArgumentException e) {
                    state = PrReview.ReviewState.COMMENTED;
                }
                prReviewRepository.save(PrReview.builder()
                    .pullRequest(pr)
                    .reviewerLogin(dto.getUser().getLogin())
                    .state(state)
                    .submittedAt(dto.getSubmittedAt())
                    .build());

                if (firstReview == null || dto.getSubmittedAt().isBefore(firstReview)) {
                    firstReview = dto.getSubmittedAt();
                }
            }
            if (firstReview != null && pr.getFirstReviewAt() == null) {
                pr.setFirstReviewAt(firstReview);
                pullRequestRepository.save(pr);
            }
        } catch (GitHubApiException e) {
            log.warn("Failed to sync reviews for PR #{}: {}", pr.getPrNumber(), e.getMessage());
        }
    }

    private String firstLine(String message) {
        if (message == null) return null;
        int nl = message.indexOf('\n');
        String line = nl > 0 ? message.substring(0, nl) : message;
        return line.length() > 255 ? line.substring(0, 255) : line;
    }
}
