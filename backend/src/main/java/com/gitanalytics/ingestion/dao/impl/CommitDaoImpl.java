package com.gitanalytics.ingestion.dao.impl;

import com.gitanalytics.ingestion.dao.CommitDao;
import com.gitanalytics.ingestion.entity.Commit;
import com.gitanalytics.ingestion.repository.CommitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CommitDaoImpl implements CommitDao {

    private final CommitRepository commitRepository;

    @Override
    public void upsert(Commit commit) {
        commitRepository.upsert(commit);
    }

    @Override
    public long countByRepoId(UUID repoId) {
        return commitRepository.countByRepoId(repoId);
    }

    @Override
    public long countByRepoSince(UUID repoId, OffsetDateTime since) {
        return commitRepository.countByRepoSince(repoId, since);
    }

    @Override
    public List<Commit> findByUserAndAuthorAndDateRange(UUID userId, String login,
                                                        OffsetDateTime from, OffsetDateTime to) {
        return commitRepository.findByUserAndAuthorAndDateRange(userId, login, from, to);
    }

    @Override
    public List<Commit> findRecentByUser(UUID userId, String login, int limit) {
        return commitRepository.findRecentByUser(userId, login, limit);
    }

    @Override
    public void deleteByRepoId(UUID repoId) {
        commitRepository.deleteByRepoId(repoId);
    }
}
