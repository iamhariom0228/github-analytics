package com.gitanalytics.ingestion.dao.impl;

import com.gitanalytics.ingestion.dao.PullRequestDao;
import com.gitanalytics.ingestion.entity.PullRequest;
import com.gitanalytics.ingestion.repository.PullRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PullRequestDaoImpl implements PullRequestDao {

    private final PullRequestRepository pullRequestRepository;

    @Override
    public Optional<PullRequest> findByRepoIdAndPrNumber(UUID repoId, Integer prNumber) {
        return pullRequestRepository.findByRepoIdAndPrNumber(repoId, prNumber);
    }

    @Override
    public List<PullRequest> findByRepoId(UUID repoId) {
        return pullRequestRepository.findByRepoId(repoId);
    }

    @Override
    public List<PullRequest> findByUserAndAuthorAndDateRange(UUID userId, String login,
                                                             OffsetDateTime from, OffsetDateTime to) {
        return pullRequestRepository.findByUserAndAuthorAndDateRange(userId, login, from, to);
    }

    @Override
    public List<PullRequest> findStalePRs(UUID userId, UUID repoId, OffsetDateTime before) {
        return pullRequestRepository.findStalePRs(userId, repoId, before);
    }

    @Override
    public List<PullRequest> findRecentByUser(UUID userId, String login, int limit) {
        return pullRequestRepository.findRecentByUser(userId, login, limit);
    }

    @Override
    public List<PullRequest> findReviewQueue(UUID userId, String login) {
        return pullRequestRepository.findReviewQueue(userId, login);
    }

    @Override
    public PullRequest save(PullRequest pullRequest) {
        return pullRequestRepository.save(pullRequest);
    }

    @Override
    public void deleteByRepoId(UUID repoId) {
        pullRequestRepository.deleteByRepoId(repoId);
    }
}
