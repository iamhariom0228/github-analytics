package com.gitanalytics.ingestion.dao;

import com.gitanalytics.ingestion.entity.TrackedRepo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackedRepoDao {
    Optional<TrackedRepo> findById(UUID id);
    List<TrackedRepo> findAll();
    List<TrackedRepo> findByUserId(UUID userId);
    List<TrackedRepo> findByUserIdAndSyncStatus(UUID userId, TrackedRepo.SyncStatus status);
    boolean existsByUserIdAndGithubRepoId(UUID userId, Long githubRepoId);
    TrackedRepo save(TrackedRepo repo);
    void delete(TrackedRepo repo);
}
