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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final ReleaseRepository releaseRepository;
    private final UserRepository userRepository;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubOAuthService gitHubOAuthService;
    private final SyncProducer syncProducer;
    private final RedisTemplate<String, Object> redisTemplate;

    @KafkaListener(topics = "ga.sync.requested", groupId = "github-analytics")
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

            syncCommits(repo, accessToken, user, since, count);
            syncPullRequests(repo, accessToken, user.getId(), since, count);
            syncRepoMeta(repo, accessToken, user.getId());
            syncReleases(repo, accessToken, user.getId(), count);

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

    private void syncCommits(TrackedRepo repo, String token, User user,
                              OffsetDateTime since, AtomicInteger count) {
        // GraphQL: fetch 100 commits per request with additions/deletions + author email — no N+1
        List<GitHubApiClient.GraphQLCommitDto> commits = gitHubApiClient.getCommitsWithStats(
            token, user.getId(), repo.getOwner(), repo.getName(), since);

        // Build email set for author resolution when GitHub user link is missing
        Set<String> knownEmails = new HashSet<>();
        if (user.getEmail() != null) knownEmails.add(user.getEmail().trim());

        for (GitHubApiClient.GraphQLCommitDto dto : commits) {
            if (dto.getCommittedDate() == null) continue;

            String authorLogin = dto.getAuthorLogin();
            Long authorGithubId = dto.getAuthorGithubId();

            // Email-based resolution: if GitHub didn't link the commit to a user account,
            // check if the commit email matches any of the user's known emails
            if (authorLogin == null && dto.getAuthorEmail() != null) {
                String email = dto.getAuthorEmail().trim().toLowerCase();
                boolean isOwner = knownEmails.stream()
                    .anyMatch(e -> e.toLowerCase().equals(email));
                if (isOwner) {
                    authorLogin = user.getUsername();
                    authorGithubId = user.getGithubId();
                }
            }

            // Fallback to committer
            if (authorLogin == null) {
                authorLogin = dto.getCommitterLogin();
                authorGithubId = dto.getCommitterGithubId();
            }

            Commit commit = Commit.builder()
                .repo(repo)
                .sha(dto.getSha())
                .authorLogin(authorLogin)
                .authorGithubId(authorGithubId)
                .messageSummary(firstLine(dto.getMessage()))
                .additions(dto.getAdditions())
                .deletions(dto.getDeletions())
                .committedAt(dto.getCommittedDate())
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

            // If changedFiles is 0 (list API doesn't return file stats), fetch individual PR
            if (dto.getChangedFiles() == 0) {
                GitHubApiClient.GitHubPRDetailDto detail =
                    gitHubApiClient.getPullRequestDetail(token, userId, repo.getOwner(), repo.getName(), dto.getNumber());
                if (detail != null) {
                    pr.setAdditions(detail.getAdditions() != null ? detail.getAdditions() : 0);
                    pr.setDeletions(detail.getDeletions() != null ? detail.getDeletions() : 0);
                    pr.setChangedFiles(detail.getChangedFiles() != null ? detail.getChangedFiles() : 0);
                }
            }

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

    private void syncRepoMeta(TrackedRepo repo, String token, UUID userId) {
        GitHubApiClient.GitHubRepoMetaDto meta = gitHubApiClient.getRepoMeta(token, userId, repo.getOwner(), repo.getName());
        if (meta != null) {
            repo.setStars(meta.getStargazersCount() != null ? meta.getStargazersCount() : 0);
            repo.setForks(meta.getForksCount() != null ? meta.getForksCount() : 0);
            repo.setWatchers(meta.getWatchersCount() != null ? meta.getWatchersCount() : 0);
            repo.setOpenIssuesCount(meta.getOpenIssuesCount() != null ? meta.getOpenIssuesCount() : 0);
            repo.setLanguage(meta.getLanguage());
            repo.setDescription(meta.getDescription());
            trackedRepoRepository.save(repo);
        }
    }

    private void syncReleases(TrackedRepo repo, String token, UUID userId, AtomicInteger count) {
        List<GitHubApiClient.GitHubReleaseDto> releases = gitHubApiClient.getReleases(token, userId, repo.getOwner(), repo.getName());
        for (GitHubApiClient.GitHubReleaseDto dto : releases) {
            if (dto.getTagName() == null) continue;
            Release release = Release.builder()
                .repo(repo)
                .tagName(dto.getTagName())
                .name(dto.getName())
                .publishedAt(dto.getPublishedAt())
                .build();
            try {
                releaseRepository.upsert(release);
            } catch (Exception e) {
                log.debug("Failed to upsert release {}: {}", dto.getTagName(), e.getMessage());
            }
        }
    }

    private String firstLine(String message) {
        if (message == null) return null;
        int nl = message.indexOf('\n');
        String line = nl > 0 ? message.substring(0, nl) : message;
        return line.length() > 255 ? line.substring(0, 255) : line;
    }
}
