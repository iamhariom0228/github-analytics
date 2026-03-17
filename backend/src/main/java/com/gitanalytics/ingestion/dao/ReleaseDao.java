package com.gitanalytics.ingestion.dao;

import com.gitanalytics.ingestion.entity.Release;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ReleaseDao {
    void upsert(Release release);
    long countByRepoId(UUID repoId);
    Optional<OffsetDateTime> findLatestPublishedAt(UUID repoId);
}
