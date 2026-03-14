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
           "AND pr.state = 'OPEN' AND pr.createdAt < :before ORDER BY pr.createdAt ASC")
    List<PullRequest> findStalePRs(UUID repoId, OffsetDateTime before);
}
