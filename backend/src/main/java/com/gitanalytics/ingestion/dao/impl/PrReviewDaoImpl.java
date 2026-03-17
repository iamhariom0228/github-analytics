package com.gitanalytics.ingestion.dao.impl;

import com.gitanalytics.ingestion.dao.PrReviewDao;
import com.gitanalytics.ingestion.entity.PrReview;
import com.gitanalytics.ingestion.repository.PrReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PrReviewDaoImpl implements PrReviewDao {

    private final PrReviewRepository prReviewRepository;

    @Override
    public PrReview save(PrReview review) {
        return prReviewRepository.save(review);
    }

    @Override
    public List<PrReview> findByPullRequestId(Long prId) {
        return prReviewRepository.findByPullRequestId(prId);
    }

    @Override
    public List<PrReview> findByUserAndReviewer(UUID userId, String login) {
        return prReviewRepository.findByUserAndReviewer(userId, login);
    }

    @Override
    public void deleteByRepoId(UUID repoId) {
        prReviewRepository.deleteByPullRequestRepoId(repoId);
    }
}
