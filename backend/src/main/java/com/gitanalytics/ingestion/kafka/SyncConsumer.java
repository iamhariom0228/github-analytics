package com.gitanalytics.ingestion.kafka;

import com.gitanalytics.auth.dao.UserDao;
import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.service.GitHubOAuthService;
import com.gitanalytics.ingestion.client.GitHubApiClient;
import com.gitanalytics.ingestion.dao.*;
import com.gitanalytics.ingestion.entity.*;
import com.gitanalytics.ingestion.repository.RepoLanguageRepository;
import com.gitanalytics.ingestion.repository.RepoStatsSnapshotRepository;
import com.gitanalytics.shared.exception.GitHubApiException;
import com.gitanalytics.shared.kafka.events.SyncRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.time.LocalDate;
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

    private final TrackedRepoDao trackedRepoDao;
    private final CommitDao commitDao;
    private final PullRequestDao pullRequestDao;
    private final PrReviewDao prReviewDao;
    private final SyncJobDao syncJobDao;
    private final ReleaseDao releaseDao;
    private final IssueDao issueDao;
    private final UserDao userDao;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubOAuthService gitHubOAuthService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RepoLanguageRepository repoLanguageRepository;
    private final RepoStatsSnapshotRepository repoStatsSnapshotRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("syncTaskExecutor")
    public void handleSyncRequested(SyncRequestedEvent event) {
        log.info("Processing sync: jobId={}, repoId={}, type={}",
            event.getSyncJobId(), event.getRepoId(), event.getSyncType());

        TrackedRepo repo = trackedRepoDao.findById(event.getRepoId()).orElse(null);
        if (repo == null) { log.warn("Repo not found: {}", event.getRepoId()); return; }

        SyncJob job = syncJobDao.findById(event.getSyncJobId()).orElse(null);
        if (job == null) return;

        repo.setSyncStatus(TrackedRepo.SyncStatus.SYNCING);
        trackedRepoDao.save(repo);
        job.setStatus(SyncJob.JobStatus.RUNNING);
        job.setStartedAt(OffsetDateTime.now());
        syncJobDao.save(job);

        String lockKey = "ga:sync:lock:" + repo.getId();
        try {
            User user = userDao.findById(event.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
            String accessToken = gitHubOAuthService.decryptAccessToken(user);

            OffsetDateTime since = "FULL_SYNC".equals(event.getSyncType()) ? null : repo.getLastSyncedAt();
            AtomicInteger count = new AtomicInteger(0);

            syncCommits(repo, accessToken, user, since, count);
            syncPullRequests(repo, accessToken, user.getId(), since, count);
            syncRepoMeta(repo, accessToken, user.getId());
            syncReleases(repo, accessToken, user.getId(), count);
            syncIssues(repo, accessToken, user.getId(), since);
            syncLanguages(repo, accessToken, user.getId());
            takeStatsSnapshot(repo);

            repo.setSyncStatus(TrackedRepo.SyncStatus.DONE);
            repo.setLastSyncedAt(OffsetDateTime.now());
            trackedRepoDao.save(repo);

            job.setStatus(SyncJob.JobStatus.DONE);
            job.setCompletedAt(OffsetDateTime.now());
            job.setRecordsProcessed(count.get());
            syncJobDao.save(job);

            // Invalidate dashboard cache directly — no need for a separate event
            try {
                redisTemplate.delete("ga:dashboard:" + event.getUserId());
            } catch (Exception cacheEx) {
                log.warn("Failed to invalidate cache after sync: {}", cacheEx.getMessage());
            }

        } catch (Exception e) {
            log.error("Sync failed for repo {}: {}", event.getRepoId(), e.getMessage(), e);
            repo.setSyncStatus(TrackedRepo.SyncStatus.FAILED);
            trackedRepoDao.save(repo);
            job.setStatus(SyncJob.JobStatus.FAILED);
            job.setCompletedAt(OffsetDateTime.now());
            job.setErrorMessage(e.getMessage());
            syncJobDao.save(job);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private void syncCommits(TrackedRepo repo, String token, User user,
                              OffsetDateTime since, AtomicInteger count) {
        List<GitHubApiClient.GraphQLCommitDto> commits = gitHubApiClient.getCommitsWithStats(
            token, user.getId(), repo.getOwner(), repo.getName(), since);

        Set<String> knownEmails = new HashSet<>();
        if (user.getEmail() != null) knownEmails.add(user.getEmail().trim());

        for (GitHubApiClient.GraphQLCommitDto dto : commits) {
            if (dto.getCommittedDate() == null) continue;

            String authorLogin = dto.getAuthorLogin();
            Long authorGithubId = dto.getAuthorGithubId();

            if (authorLogin == null && dto.getAuthorEmail() != null) {
                String email = dto.getAuthorEmail().trim().toLowerCase();
                if (knownEmails.stream().anyMatch(e -> e.toLowerCase().equals(email))) {
                    authorLogin = user.getUsername();
                    authorGithubId = user.getGithubId();
                }
            }

            if (authorLogin == null) {
                authorLogin = dto.getCommitterLogin();
                authorGithubId = dto.getCommitterGithubId();
            }

            Commit commit = Commit.builder()
                .repo(repo).sha(dto.getSha())
                .authorLogin(authorLogin).authorGithubId(authorGithubId)
                .messageSummary(firstLine(dto.getMessage()))
                .additions(dto.getAdditions()).deletions(dto.getDeletions())
                .committedAt(dto.getCommittedDate())
                .build();
            try {
                commitDao.upsert(commit);
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

            PullRequest pr = pullRequestDao.findByRepoIdAndPrNumber(repo.getId(), dto.getNumber())
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

            if (dto.getChangedFiles() == 0) {
                GitHubApiClient.GitHubPRDetailDto detail =
                    gitHubApiClient.getPullRequestDetail(token, userId, repo.getOwner(), repo.getName(), dto.getNumber());
                if (detail != null) {
                    pr.setAdditions(detail.getAdditions() != null ? detail.getAdditions() : 0);
                    pr.setDeletions(detail.getDeletions() != null ? detail.getDeletions() : 0);
                    pr.setChangedFiles(detail.getChangedFiles() != null ? detail.getChangedFiles() : 0);
                }
            }

            PullRequest savedPr = pullRequestDao.save(pr);
            count.incrementAndGet();
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
                prReviewDao.save(PrReview.builder()
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
                pullRequestDao.save(pr);
            }
        } catch (GitHubApiException e) {
            log.warn("Failed to sync reviews for PR #{}: {}", pr.getPrNumber(), e.getMessage());
        }
    }

    private void syncRepoMeta(TrackedRepo repo, String token, UUID userId) {
        GitHubApiClient.GitHubRepoMetaDto meta = gitHubApiClient.getRepoMeta(
            token, userId, repo.getOwner(), repo.getName());
        if (meta != null) {
            repo.setStars(meta.getStargazersCount() != null ? meta.getStargazersCount() : 0);
            repo.setForks(meta.getForksCount() != null ? meta.getForksCount() : 0);
            repo.setWatchers(meta.getWatchersCount() != null ? meta.getWatchersCount() : 0);
            repo.setOpenIssuesCount(meta.getOpenIssuesCount() != null ? meta.getOpenIssuesCount() : 0);
            repo.setLanguage(meta.getLanguage());
            repo.setDescription(meta.getDescription());
            trackedRepoDao.save(repo);
            // Capture a snapshot immediately so trend data is available right after sync
            repoStatsSnapshotRepository.upsert(
                repo.getId(), LocalDate.now(),
                repo.getStars(), repo.getForks(), repo.getWatchers());
        }
    }

    private void syncReleases(TrackedRepo repo, String token, UUID userId, AtomicInteger count) {
        List<GitHubApiClient.GitHubReleaseDto> releases = gitHubApiClient.getReleases(
            token, userId, repo.getOwner(), repo.getName());
        for (GitHubApiClient.GitHubReleaseDto dto : releases) {
            if (dto.getTagName() == null) continue;
            try {
                releaseDao.upsert(Release.builder()
                    .repo(repo).tagName(dto.getTagName())
                    .name(dto.getName()).publishedAt(dto.getPublishedAt())
                    .build());
            } catch (Exception e) {
                log.debug("Failed to upsert release {}: {}", dto.getTagName(), e.getMessage());
            }
        }
    }

    private void syncIssues(TrackedRepo repo, String token, UUID userId, OffsetDateTime since) {
        try {
            List<GitHubApiClient.GitHubIssueDto> issues = gitHubApiClient.getIssues(
                token, userId, repo.getOwner(), repo.getName(), since);
            for (GitHubApiClient.GitHubIssueDto gi : issues) {
                if (gi.getNumber() == null) continue;
                try {
                    issueDao.upsert(Issue.builder()
                        .repo(repo)
                        .issueNumber(gi.getNumber())
                        .title(gi.getTitle())
                        .authorLogin(gi.getUser() != null ? gi.getUser().getLogin() : null)
                        .state(gi.getState())
                        .createdAt(gi.getCreatedAt())
                        .closedAt(gi.getClosedAt())
                        .build());
                } catch (Exception e) {
                    log.debug("Failed to upsert issue #{}: {}", gi.getNumber(), e.getMessage());
                }
            }
            log.info("Synced {} issues for {}", issues.size(), repo.getFullName());
        } catch (Exception e) {
            log.warn("Failed to sync issues for {}: {}", repo.getFullName(), e.getMessage());
        }
    }

    private void syncLanguages(TrackedRepo repo, String token, UUID userId) {
        try {
            java.util.Map<String, Long> langs = gitHubApiClient.getLanguages(
                token, userId, repo.getOwner(), repo.getName());
            for (java.util.Map.Entry<String, Long> entry : langs.entrySet()) {
                repoLanguageRepository.upsert(repo.getId(), entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            log.warn("Failed to sync languages for {}: {}", repo.getFullName(), e.getMessage());
        }
    }

    private void takeStatsSnapshot(TrackedRepo repo) {
        try {
            repoStatsSnapshotRepository.upsert(
                repo.getId(),
                java.time.LocalDate.now(),
                repo.getStars() != null ? repo.getStars() : 0,
                repo.getForks() != null ? repo.getForks() : 0,
                repo.getWatchers() != null ? repo.getWatchers() : 0
            );
        } catch (Exception e) {
            log.warn("Failed to take stats snapshot for {}: {}", repo.getFullName(), e.getMessage());
        }
    }

    private String firstLine(String message) {
        if (message == null) return null;
        int nl = message.indexOf('\n');
        String line = nl > 0 ? message.substring(0, nl) : message;
        return line.length() > 255 ? line.substring(0, 255) : line;
    }
}
