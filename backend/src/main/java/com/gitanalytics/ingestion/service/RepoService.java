package com.gitanalytics.ingestion.service;

import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.auth.service.GitHubOAuthService;
import com.gitanalytics.ingestion.client.GitHubApiClient;
import com.gitanalytics.ingestion.dto.AddRepoRequest;
import com.gitanalytics.ingestion.dto.RepoDto;
import com.gitanalytics.ingestion.dto.SyncStatusDto;
import com.gitanalytics.ingestion.entity.SyncJob;
import com.gitanalytics.ingestion.entity.TrackedRepo;
import com.gitanalytics.ingestion.kafka.SyncProducer;
import com.gitanalytics.ingestion.repository.SyncJobRepository;
import com.gitanalytics.ingestion.repository.TrackedRepoRepository;
import com.gitanalytics.shared.exception.ResourceNotFoundException;
import com.gitanalytics.shared.exception.UnauthorizedException;
import com.gitanalytics.shared.kafka.events.SyncRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepoService {

    private final TrackedRepoRepository trackedRepoRepository;
    private final SyncJobRepository syncJobRepository;
    private final UserRepository userRepository;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubOAuthService gitHubOAuthService;
    private final SyncProducer syncProducer;
    private final RedisTemplate<String, Object> redisTemplate;

    public List<RepoDto> getUserRepos(UUID userId) {
        return trackedRepoRepository.findByUserId(userId).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public RepoDto addRepo(UUID userId, AddRepoRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Fetch repo details from GitHub
        String accessToken = gitHubOAuthService.decryptAccessToken(user);
        List<GitHubApiClient.GitHubRepoDto> repos = gitHubApiClient.getUserRepos(accessToken, userId);
        GitHubApiClient.GitHubRepoDto ghRepo = repos.stream()
            .filter(r -> r.getFullName().equalsIgnoreCase(request.getOwner() + "/" + request.getName()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found on GitHub"));

        if (trackedRepoRepository.existsByUserIdAndGithubRepoId(userId, ghRepo.getId())) {
            throw new IllegalStateException("Repository already tracked");
        }

        TrackedRepo repo = trackedRepoRepository.save(TrackedRepo.builder()
            .user(user)
            .owner(ghRepo.getOwner().getLogin())
            .name(ghRepo.getName())
            .fullName(ghRepo.getFullName())
            .githubRepoId(ghRepo.getId())
            .isPrivate(ghRepo.isPrivateRepo())
            .syncStatus(TrackedRepo.SyncStatus.PENDING)
            .build());

        // Trigger initial full sync
        triggerSync(user, repo, "FULL_SYNC");

        return toDto(repo);
    }

    @Transactional
    public void deleteRepo(UUID userId, UUID repoId) {
        TrackedRepo repo = trackedRepoRepository.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
        if (!repo.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Not your repository");
        }
        trackedRepoRepository.delete(repo);
    }

    @Transactional
    public SyncStatusDto triggerManualSync(UUID userId, UUID repoId) {
        TrackedRepo repo = trackedRepoRepository.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
        if (!repo.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Not your repository");
        }
        User user = userRepository.findById(userId).orElseThrow();
        SyncJob job = triggerSync(user, repo, "INCREMENTAL_SYNC");
        return new SyncStatusDto(job.getId(), job.getStatus().name(), repo.getSyncStatus().name());
    }

    public SyncStatusDto getSyncStatus(UUID userId, UUID repoId) {
        TrackedRepo repo = trackedRepoRepository.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
        if (!repo.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Not your repository");
        }
        SyncJob job = syncJobRepository.findTopByRepoIdOrderByCreatedAtDesc(repoId).orElse(null);
        return new SyncStatusDto(
            job != null ? job.getId() : null,
            job != null ? job.getStatus().name() : "NONE",
            repo.getSyncStatus().name()
        );
    }

    public List<GitHubApiClient.GitHubRepoDto> getSuggestions(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String accessToken = gitHubOAuthService.decryptAccessToken(user);
        List<GitHubApiClient.GitHubRepoDto> all = gitHubApiClient.getUserRepos(accessToken, userId);

        // Filter out already tracked repos
        List<Long> trackedIds = trackedRepoRepository.findByUserId(userId)
            .stream().map(TrackedRepo::getGithubRepoId).toList();

        return all.stream()
            .filter(r -> !trackedIds.contains(r.getId()))
            .limit(20)
            .toList();
    }

    private SyncJob triggerSync(User user, TrackedRepo repo, String syncType) {
        // Distributed lock to prevent concurrent syncs
        String lockKey = "ga:sync:lock:" + repo.getId();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", 5, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(locked)) {
            throw new IllegalStateException("Sync already in progress for this repository");
        }

        SyncJob job = syncJobRepository.save(SyncJob.builder()
            .user(user)
            .repo(repo)
            .jobType(SyncJob.JobType.valueOf(syncType))
            .status(SyncJob.JobStatus.PENDING)
            .build());

        syncProducer.publishSyncRequested(new SyncRequestedEvent(
            job.getId(), user.getId(), repo.getId(), syncType
        ));

        return job;
    }

    private RepoDto toDto(TrackedRepo repo) {
        return new RepoDto(
            repo.getId(),
            repo.getOwner(),
            repo.getName(),
            repo.getFullName(),
            repo.isPrivate(),
            repo.getSyncStatus().name(),
            repo.getLastSyncedAt()
        );
    }
}
