package com.gitanalytics.ingestion.repository;

import com.gitanalytics.ingestion.entity.Commit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CommitRepository extends JpaRepository<Commit, Long> {

    long countByRepoId(UUID repoId);

    @Modifying
    @Query("DELETE FROM Commit c WHERE c.repo.id = :repoId")
    void deleteByRepoId(UUID repoId);

    @Query("SELECT c FROM Commit c WHERE c.repo.user.id = :userId AND c.authorLogin = :login " +
           "AND c.committedAt BETWEEN :from AND :to ORDER BY c.committedAt DESC")
    List<Commit> findByUserAndAuthorAndDateRange(UUID userId, String login,
                                                  OffsetDateTime from, OffsetDateTime to);

    @Query("SELECT COUNT(c) FROM Commit c WHERE c.repo.id = :repoId " +
           "AND c.committedAt > :since")
    long countByRepoSince(UUID repoId, OffsetDateTime since);

    @Query("SELECT c FROM Commit c WHERE c.repo.user.id = :userId AND c.authorLogin = :login " +
           "ORDER BY c.committedAt DESC LIMIT :limit")
    List<Commit> findRecentByUser(UUID userId, String login, int limit);

    @Transactional
    @Query(value = "INSERT INTO commits (repo_id, sha, author_login, author_github_id, " +
                   "message_summary, additions, deletions, committed_at) " +
                   "VALUES (:#{#c.repo.id}, :#{#c.sha}, :#{#c.authorLogin}, :#{#c.authorGithubId}, " +
                   ":#{#c.messageSummary}, :#{#c.additions}, :#{#c.deletions}, :#{#c.committedAt}) " +
                   "ON CONFLICT (repo_id, sha) DO UPDATE SET " +
                   "author_login = EXCLUDED.author_login, " +
                   "author_github_id = EXCLUDED.author_github_id, " +
                   "additions = EXCLUDED.additions, " +
                   "deletions = EXCLUDED.deletions",
           nativeQuery = true)
    @Modifying
    void upsert(@org.springframework.data.repository.query.Param("c") Commit commit);
}
