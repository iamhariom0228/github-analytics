package com.gitanalytics.ingestion.repository;

import com.gitanalytics.ingestion.entity.PrReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PrReviewRepository extends JpaRepository<PrReview, Long> {

    List<PrReview> findByPullRequestId(Long prId);

    @Query("SELECT r FROM PrReview r WHERE r.pullRequest.repo.user.id = :userId " +
           "AND r.reviewerLogin = :login ORDER BY r.submittedAt DESC")
    List<PrReview> findByUserAndReviewer(UUID userId, String login);
}
