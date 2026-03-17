package com.gitanalytics.ingestion.dao;

import com.gitanalytics.ingestion.entity.SyncJob;

import java.util.Optional;
import java.util.UUID;

public interface SyncJobDao {
    SyncJob save(SyncJob syncJob);
    Optional<SyncJob> findById(UUID id);
    Optional<SyncJob> findLatestByRepoId(UUID repoId);
}
