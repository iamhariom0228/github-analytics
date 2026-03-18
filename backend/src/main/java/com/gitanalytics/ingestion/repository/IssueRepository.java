package com.gitanalytics.ingestion.repository;

import com.gitanalytics.ingestion.entity.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {

    @Transactional
    @Modifying
    @Query(value = """
        INSERT INTO issues (repo_id, issue_number, title, author_login, state, created_at, closed_at)
        VALUES (:#{#i.repo.id}, :#{#i.issueNumber}, :#{#i.title}, :#{#i.authorLogin},
                :#{#i.state}, :#{#i.createdAt}, :#{#i.closedAt})
        ON CONFLICT (repo_id, issue_number) DO UPDATE
          SET title        = EXCLUDED.title,
              state        = EXCLUDED.state,
              closed_at    = EXCLUDED.closed_at
        """, nativeQuery = true)
    void upsert(@Param("i") Issue issue);
}
