package com.gitanalytics.ingestion.repository;

import com.gitanalytics.ingestion.entity.RepoStatsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface RepoStatsSnapshotRepository extends JpaRepository<RepoStatsSnapshot, Long> {

    @Query(value = """
        INSERT INTO repo_stats_snapshots (repo_id, snapshotted_on, stars, forks, watchers)
        VALUES (:repoId, :snappedOn, :stars, :forks, :watchers)
        ON CONFLICT (repo_id, snapshotted_on) DO UPDATE
          SET stars = EXCLUDED.stars, forks = EXCLUDED.forks, watchers = EXCLUDED.watchers
        """, nativeQuery = true)
    @Modifying
    @Transactional
    void upsert(@Param("repoId") UUID repoId, @Param("snappedOn") LocalDate snappedOn,
                @Param("stars") int stars, @Param("forks") int forks, @Param("watchers") int watchers);
}
