package com.gitanalytics.ingestion.repository;

import com.gitanalytics.ingestion.entity.TrackedRepo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrackedRepoRepository extends JpaRepository<TrackedRepo, UUID> {
    List<TrackedRepo> findByUserId(UUID userId);
    Optional<TrackedRepo> findByUserIdAndGithubRepoId(UUID userId, Long githubRepoId);
    boolean existsByUserIdAndGithubRepoId(UUID userId, Long githubRepoId);
    List<TrackedRepo> findByUserIdAndSyncStatus(UUID userId, TrackedRepo.SyncStatus syncStatus);
}
