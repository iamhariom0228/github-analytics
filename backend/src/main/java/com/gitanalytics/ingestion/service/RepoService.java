package com.gitanalytics.ingestion.service;

import com.gitanalytics.auth.dao.UserDao;
import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.service.GitHubOAuthService;
import com.gitanalytics.ingestion.client.GitHubApiClient;
import com.gitanalytics.ingestion.dao.SyncJobDao;
import com.gitanalytics.ingestion.dao.TrackedRepoDao;
import com.gitanalytics.ingestion.dto.AddRepoRequest;
import com.gitanalytics.ingestion.dto.RepoDto;
import com.gitanalytics.ingestion.dto.SyncStatusDto;
import com.gitanalytics.ingestion.entity.SyncJob;
import com.gitanalytics.ingestion.entity.TrackedRepo;
import com.gitanalytics.shared.config.AppProperties;
import com.gitanalytics.shared.exception.ResourceNotFoundException;
import com.gitanalytics.shared.exception.UnauthorizedException;
import com.gitanalytics.shared.events.SyncRequestedEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepoService {

    private final TrackedRepoDao trackedRepoDao;
    private final SyncJobDao syncJobDao;
    private final UserDao userDao;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubOAuthService gitHubOAuthService;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties appProperties;

    @PostConstruct
    public void clearStaleSyncLocks() {
        try {
            Set<String> keys = redisTemplate.keys("ga:sync:lock:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Cleared {} stale sync lock(s) on startup", keys.size());
            }
        } catch (Exception e) {
            log.warn("Could not clear stale sync locks on startup: {}", e.getMessage());
        }
    }

    public List<RepoDto> getUserRepos(UUID userId, int page, int size) {
        return trackedRepoDao.findByUserId(userId).stream()
            .skip((long) page * size)
            .limit(size)
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public RepoDto addRepo(UUID userId, AddRepoRequest request) {
        User user = userDao.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String accessToken = gitHubOAuthService.decryptAccessToken(user);
        List<GitHubApiClient.GitHubRepoDto> repos = gitHubApiClient.getUserRepos(accessToken, userId);
        GitHubApiClient.GitHubRepoDto ghRepo = repos.stream()
            .filter(r -> r.getFullName().equalsIgnoreCase(request.getOwner() + "/" + request.getName()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found on GitHub"));

        if (trackedRepoDao.existsByUserIdAndGithubRepoId(userId, ghRepo.getId())) {
            throw new IllegalStateException("Repository already tracked");
        }

        TrackedRepo repo = trackedRepoDao.save(TrackedRepo.builder()
            .user(user)
            .owner(ghRepo.getOwner().getLogin())
            .name(ghRepo.getName())
            .fullName(ghRepo.getFullName())
            .githubRepoId(ghRepo.getId())
            .isPrivate(ghRepo.isPrivateRepo())
            .syncStatus(TrackedRepo.SyncStatus.PENDING)
            .build());

        String webhookUrl = appProperties.getGithub().getWebhookUrl();
        String webhookSecret = appProperties.getGithub().getWebhookSecret();
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            try {
                gitHubApiClient.createWebhook(accessToken, userId,
                    ghRepo.getOwner().getLogin(), ghRepo.getName(), webhookUrl, webhookSecret);
                log.info("Registered GitHub webhook for {}", ghRepo.getFullName());
            } catch (Exception e) {
                log.warn("Failed to register webhook for {}: {}", ghRepo.getFullName(), e.getMessage());
            }
        }

        triggerSync(user, repo, "FULL_SYNC");
        return toDto(repo);
    }

    @Transactional
    public void deleteRepo(UUID userId, UUID repoId) {
        TrackedRepo repo = trackedRepoDao.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
        if (!repo.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Not your repository");
        }
        trackedRepoDao.delete(repo);
    }

    @Transactional
    public SyncStatusDto triggerManualSync(UUID userId, UUID repoId) {
        TrackedRepo repo = trackedRepoDao.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
        if (!repo.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Not your repository");
        }
        User user = userDao.findById(userId).orElseThrow();
        SyncJob job = triggerSync(user, repo, "FULL_SYNC");
        return new SyncStatusDto(job.getId(), job.getStatus().name(), repo.getSyncStatus().name());
    }

    public SyncStatusDto getSyncStatus(UUID userId, UUID repoId) {
        TrackedRepo repo = trackedRepoDao.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
        if (!repo.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Not your repository");
        }
        SyncJob job = syncJobDao.findLatestByRepoId(repoId).orElse(null);
        return new SyncStatusDto(
            job != null ? job.getId() : null,
            job != null ? job.getStatus().name() : "NONE",
            repo.getSyncStatus().name()
        );
    }

    public String forkRepo(UUID userId, String owner, String repo) {
        User user = userDao.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String accessToken = gitHubOAuthService.decryptAccessToken(user);
        return gitHubApiClient.forkRepo(accessToken, userId, owner, repo);
    }

    public List<GitHubApiClient.GitHubRepoDto> getSuggestions(UUID userId) {
        User user = userDao.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String accessToken = gitHubOAuthService.decryptAccessToken(user);
        List<GitHubApiClient.GitHubRepoDto> all = gitHubApiClient.getUserRepos(accessToken, userId);

        List<Long> trackedIds = trackedRepoDao.findByUserId(userId)
            .stream().map(TrackedRepo::getGithubRepoId).toList();

        return all.stream()
            .filter(r -> !trackedIds.contains(r.getId()))
            .limit(20)
            .toList();
    }

    private SyncJob triggerSync(User user, TrackedRepo repo, String syncType) {
        String lockKey = "ga:sync:lock:" + repo.getId();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", appProperties.getRedis().getSyncLockTtl(), TimeUnit.SECONDS);
        // TRUE = lock acquired; FALSE = already locked; null = Redis error → treat as locked to be safe
        if (!Boolean.TRUE.equals(locked)) {
            throw new IllegalStateException("Sync already in progress for this repository");
        }

        SyncJob job = syncJobDao.save(SyncJob.builder()
            .user(user)
            .repo(repo)
            .jobType(SyncJob.JobType.valueOf(syncType))
            .status(SyncJob.JobStatus.PENDING)
            .build());

        SyncRequestedEvent event = new SyncRequestedEvent(job.getId(), user.getId(), repo.getId(), syncType);
        eventPublisher.publishEvent(event);

        return job;
    }

    private RepoDto toDto(TrackedRepo repo) {
        return new RepoDto(
            repo.getId(), repo.getOwner(), repo.getName(), repo.getFullName(),
            repo.isPrivate(), repo.getSyncStatus().name(), repo.getLastSyncedAt(),
            repo.getStars(), repo.getForks(), repo.getWatchers(), repo.getOpenIssuesCount(),
            repo.getLanguage(), repo.getDescription()
        );
    }
}
