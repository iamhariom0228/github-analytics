package com.gitanalytics.ingestion.dao;

import com.gitanalytics.ingestion.entity.PullRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PullRequestDao {
    Optional<PullRequest> findByRepoIdAndPrNumber(UUID repoId, Integer prNumber);
    List<PullRequest> findByRepoId(UUID repoId);
    List<PullRequest> findByUserAndAuthorAndDateRange(UUID userId, String login, OffsetDateTime from, OffsetDateTime to);
    List<PullRequest> findStalePRs(UUID repoId, OffsetDateTime before);
    List<PullRequest> findRecentByUser(UUID userId, String login, int limit);
    List<PullRequest> findReviewQueue(UUID userId, String login);
    PullRequest save(PullRequest pullRequest);
    void deleteByRepoId(UUID repoId);
}
