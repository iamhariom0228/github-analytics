package com.gitanalytics.ingestion.repository;

import com.gitanalytics.ingestion.entity.PullRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

    Optional<PullRequest> findByRepoIdAndPrNumber(UUID repoId, Integer prNumber);

    List<PullRequest> findByRepoId(UUID repoId);

    @Modifying
    @Query("DELETE FROM PullRequest pr WHERE pr.repo.id = :repoId")
    void deleteByRepoId(UUID repoId);

    @Query("SELECT pr FROM PullRequest pr WHERE pr.repo.user.id = :userId " +
           "AND pr.authorLogin = :login AND pr.createdAt BETWEEN :from AND :to")
    List<PullRequest> findByUserAndAuthorAndDateRange(UUID userId, String login,
                                                       OffsetDateTime from, OffsetDateTime to);

    @Query("SELECT pr FROM PullRequest pr WHERE pr.repo.id = :repoId " +
           "AND pr.repo.user.id = :userId " +
           "AND pr.state = 'OPEN' AND pr.createdAt < :before ORDER BY pr.createdAt ASC")
    List<PullRequest> findStalePRs(UUID userId, UUID repoId, OffsetDateTime before);

    @Query("SELECT pr FROM PullRequest pr WHERE pr.repo.user.id = :userId " +
           "AND pr.authorLogin = :login ORDER BY pr.createdAt DESC LIMIT :limit")
    List<PullRequest> findRecentByUser(UUID userId, String login, int limit);

    @Query("SELECT pr FROM PullRequest pr WHERE pr.repo.user.id = :userId " +
           "AND pr.state = 'OPEN' AND pr.authorLogin <> :login " +
           "AND pr.id NOT IN (SELECT r.pullRequest.id FROM PrReview r WHERE r.reviewerLogin = :login) " +
           "ORDER BY pr.createdAt ASC")
    List<PullRequest> findReviewQueue(UUID userId, String login);
}
