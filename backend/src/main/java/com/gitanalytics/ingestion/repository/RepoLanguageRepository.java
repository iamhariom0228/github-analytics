package com.gitanalytics.ingestion.repository;

import com.gitanalytics.ingestion.entity.RepoLanguage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface RepoLanguageRepository extends JpaRepository<RepoLanguage, Long> {

    @Transactional
    @Modifying
    @Query(value = """
        INSERT INTO repo_languages (repo_id, language, bytes)
        VALUES (:repoId, :language, :bytes)
        ON CONFLICT (repo_id, language) DO UPDATE
          SET bytes = EXCLUDED.bytes
        """, nativeQuery = true)
    void upsert(@Param("repoId") UUID repoId, @Param("language") String language, @Param("bytes") long bytes);
}
