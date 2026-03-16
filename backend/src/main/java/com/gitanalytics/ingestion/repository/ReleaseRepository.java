package com.gitanalytics.ingestion.repository;

import com.gitanalytics.ingestion.entity.Release;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReleaseRepository extends JpaRepository<Release, Long> {
    @Query("SELECT MAX(r.publishedAt) FROM Release r WHERE r.repo.id = :repoId")
    Optional<OffsetDateTime> findLatestPublishedAt(UUID repoId);

    long countByRepoId(UUID repoId);

    @Transactional
    @Query(value = "INSERT INTO releases (repo_id, tag_name, name, published_at) VALUES (:#{#r.repo.id}, :#{#r.tagName}, :#{#r.name}, :#{#r.publishedAt}) ON CONFLICT (repo_id, tag_name) DO UPDATE SET name = EXCLUDED.name, published_at = EXCLUDED.published_at", nativeQuery = true)
    @Modifying
    void upsert(@org.springframework.data.repository.query.Param("r") Release release);
}
