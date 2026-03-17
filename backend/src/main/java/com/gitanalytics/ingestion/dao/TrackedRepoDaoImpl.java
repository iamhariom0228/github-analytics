package com.gitanalytics.ingestion.dao;

import com.gitanalytics.ingestion.entity.TrackedRepo;
import com.gitanalytics.ingestion.repository.TrackedRepoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TrackedRepoDaoImpl implements TrackedRepoDao {

    private final TrackedRepoRepository trackedRepoRepository;

    @Override
    public Optional<TrackedRepo> findById(UUID id) {
        return trackedRepoRepository.findById(id);
    }

    @Override
    public List<TrackedRepo> findAll() {
        return trackedRepoRepository.findAll();
    }

    @Override
    public List<TrackedRepo> findByUserId(UUID userId) {
        return trackedRepoRepository.findByUserId(userId);
    }

    @Override
    public List<TrackedRepo> findByUserIdAndSyncStatus(UUID userId, TrackedRepo.SyncStatus status) {
        return trackedRepoRepository.findByUserIdAndSyncStatus(userId, status);
    }

    @Override
    public boolean existsByUserIdAndGithubRepoId(UUID userId, Long githubRepoId) {
        return trackedRepoRepository.existsByUserIdAndGithubRepoId(userId, githubRepoId);
    }

    @Override
    public TrackedRepo save(TrackedRepo repo) {
        return trackedRepoRepository.save(repo);
    }

    @Override
    public void delete(TrackedRepo repo) {
        trackedRepoRepository.delete(repo);
    }
}
