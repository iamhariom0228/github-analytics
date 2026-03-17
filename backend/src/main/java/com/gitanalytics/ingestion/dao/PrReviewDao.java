package com.gitanalytics.ingestion.dao;

import com.gitanalytics.ingestion.entity.PrReview;

import java.util.List;
import java.util.UUID;

public interface PrReviewDao {
    PrReview save(PrReview review);
    List<PrReview> findByPullRequestId(Long prId);
    List<PrReview> findByUserAndReviewer(UUID userId, String login);
    void deleteByRepoId(UUID repoId);
}
