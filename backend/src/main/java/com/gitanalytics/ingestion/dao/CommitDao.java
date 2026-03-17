package com.gitanalytics.ingestion.dao;

import com.gitanalytics.ingestion.entity.Commit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface CommitDao {
    void upsert(Commit commit);
    long countByRepoId(UUID repoId);
    long countByRepoSince(UUID repoId, OffsetDateTime since);
    List<Commit> findByUserAndAuthorAndDateRange(UUID userId, String login, OffsetDateTime from, OffsetDateTime to);
    void deleteByRepoId(UUID repoId);
}
