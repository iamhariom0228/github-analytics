package com.gitanalytics.ingestion.dao;

import com.gitanalytics.ingestion.entity.Release;
import com.gitanalytics.ingestion.repository.ReleaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReleaseDaoImpl implements ReleaseDao {

    private final ReleaseRepository releaseRepository;

    @Override
    public void upsert(Release release) {
        releaseRepository.upsert(release);
    }

    @Override
    public long countByRepoId(UUID repoId) {
        return releaseRepository.countByRepoId(repoId);
    }

    @Override
    public Optional<OffsetDateTime> findLatestPublishedAt(UUID repoId) {
        return releaseRepository.findLatestPublishedAt(repoId);
    }
}
