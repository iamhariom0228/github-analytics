package com.gitanalytics.ingestion.repository;

import com.gitanalytics.ingestion.entity.SyncJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, UUID> {
    Optional<SyncJob> findTopByRepoIdOrderByCreatedAtDesc(UUID repoId);
}
