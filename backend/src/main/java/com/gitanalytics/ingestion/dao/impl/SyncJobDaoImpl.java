package com.gitanalytics.ingestion.dao.impl;

import com.gitanalytics.ingestion.dao.SyncJobDao;
import com.gitanalytics.ingestion.entity.SyncJob;
import com.gitanalytics.ingestion.repository.SyncJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SyncJobDaoImpl implements SyncJobDao {

    private final SyncJobRepository syncJobRepository;

    @Override
    public SyncJob save(SyncJob syncJob) {
        return syncJobRepository.save(syncJob);
    }

    @Override
    public Optional<SyncJob> findById(UUID id) {
        return syncJobRepository.findById(id);
    }

    @Override
    public Optional<SyncJob> findLatestByRepoId(UUID repoId) {
        return syncJobRepository.findTopByRepoIdOrderByCreatedAtDesc(repoId);
    }
}
