package com.gitanalytics.analytics.repository;

import com.gitanalytics.ingestion.entity.Commit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalyticsRepository extends Repository<Commit, Long> {

    // ── Heatmap ──────────────────────────────────────────────────────────────

    @Query(nativeQuery = true, value = """
            SELECT CAST(EXTRACT(DOW FROM c.committed_at AT TIME ZONE :tz) AS int) AS day,
                   CAST(EXTRACT(HOUR FROM c.committed_at AT TIME ZONE :tz) AS int) AS hour,
                   CAST(COUNT(*) AS int) AS count
            FROM commits c
            JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId
              AND c.author_login = (SELECT username FROM users WHERE id = :userId)
              AND (CAST(:from AS timestamptz) IS NULL OR c.committed_at >= :from)
              AND (CAST(:to   AS timestamptz) IS NULL OR c.committed_at <= :to)
            GROUP BY day, hour
            ORDER BY day, hour
            """)
    List<Object[]> getCommitHeatmap(@Param("userId") UUID userId, @Param("tz") String tz,
                                    @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(nativeQuery = true, value = """
            SELECT CAST(EXTRACT(DOW FROM c.committed_at AT TIME ZONE :tz) AS int) AS day,
                   CAST(EXTRACT(HOUR FROM c.committed_at AT TIME ZONE :tz) AS int) AS hour,
                   CAST(COUNT(*) AS int) AS count
            FROM commits c
            JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId
              AND c.author_login = (SELECT username FROM users WHERE id = :userId)
              AND r.id = :repoId
              AND (CAST(:from AS timestamptz) IS NULL OR c.committed_at >= :from)
              AND (CAST(:to   AS timestamptz) IS NULL OR c.committed_at <= :to)
            GROUP BY day, hour
            ORDER BY day, hour
            """)
    List<Object[]> getCommitHeatmapByRepo(@Param("userId") UUID userId, @Param("tz") String tz,
                                          @Param("repoId") UUID repoId,
                                          @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    // ── PR Lifecycle ─────────────────────────────────────────────────────────

    @Query(nativeQuery = true, value = """
            SELECT
              AVG(EXTRACT(EPOCH FROM (pr.first_review_at - pr.created_at))/3600),
              AVG(EXTRACT(EPOCH FROM (pr.merged_at - pr.created_at))/3600),
              COUNT(*) FILTER (WHERE pr.merged_at IS NOT NULL),
              COUNT(*)
            FROM pull_requests pr
            JOIN tracked_repos r ON pr.repo_id = r.id
            WHERE r.user_id = :userId
              AND pr.author_login = (SELECT username FROM users WHERE id = :userId)
              AND pr.created_at BETWEEN :from AND :to
            """)
    Object[] getPRLifecycleStats(@Param("userId") UUID userId,
                                 @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    // ── Team Leaderboard ─────────────────────────────────────────────────────

    @Query(nativeQuery = true, value = """
            SELECT c.author_login,
                   COUNT(*) AS commits,
                   CAST(SUM(c.additions) AS bigint) AS lines_added,
                   CAST(SUM(c.deletions) AS bigint) AS lines_removed
            FROM commits c
            JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId AND r.id = :repoId
              AND c.committed_at BETWEEN :from AND :to
            GROUP BY c.author_login
            ORDER BY commits DESC
            LIMIT 20
            """)
    List<Object[]> getTeamLeaderboard(@Param("userId") UUID userId, @Param("repoId") UUID repoId,
                                      @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    // ── Bus Factor ───────────────────────────────────────────────────────────

    @Query(nativeQuery = true, value = """
            SELECT c.author_login, COUNT(*) AS commits
            FROM commits c
            JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId AND r.id = :repoId
            GROUP BY c.author_login
            ORDER BY commits DESC
            """)
    List<Object[]> getBusFactorRows(@Param("userId") UUID userId, @Param("repoId") UUID repoId);

    // ── Reviews Summary ──────────────────────────────────────────────────────

    @Query(nativeQuery = true, value = """
            SELECT
              COUNT(*),
              COUNT(*) FILTER (WHERE r.state = 'APPROVED'),
              COUNT(*) FILTER (WHERE r.state = 'CHANGES_REQUESTED'),
              COUNT(*) FILTER (WHERE r.state = 'COMMENTED'),
              COUNT(DISTINCT r.pr_id)
            FROM pr_reviews r
            JOIN pull_requests pr ON r.pr_id = pr.id
            JOIN tracked_repos tr ON pr.repo_id = tr.id
            WHERE tr.user_id = :userId
              AND r.reviewer_login = :login
              AND r.submitted_at BETWEEN :from AND :to
            """)
    Object[] getReviewsSummaryRow(@Param("userId") UUID userId, @Param("login") String login,
                                  @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    // ── Repo Health Signals ───────────────────────────────────────────────────

    @Query(nativeQuery = true, value =
            "SELECT COUNT(*) FROM commits WHERE repo_id = :repoId AND committed_at > :since")
    Long countRecentCommits(@Param("repoId") UUID repoId, @Param("since") OffsetDateTime since);

    @Query(nativeQuery = true, value =
            "SELECT COUNT(DISTINCT pr.id), COUNT(DISTINCT rv.pr_id) " +
            "FROM pull_requests pr LEFT JOIN pr_reviews rv ON rv.pr_id = pr.id " +
            "WHERE pr.repo_id = :repoId AND pr.created_at > :since")
    Object[] getPRReviewCoverage(@Param("repoId") UUID repoId, @Param("since") OffsetDateTime since);

    @Query(nativeQuery = true, value =
            "SELECT COALESCE(author_login, 'unknown'), COUNT(*) AS cnt FROM commits " +
            "WHERE repo_id = :repoId AND committed_at > :since " +
            "GROUP BY author_login ORDER BY cnt DESC LIMIT 1")
    List<Object[]> getTopAuthorRows(@Param("repoId") UUID repoId, @Param("since") OffsetDateTime since);

    @Query(nativeQuery = true, value =
            "SELECT AVG(EXTRACT(EPOCH FROM (merged_at - created_at))/3600) " +
            "FROM pull_requests WHERE repo_id = :repoId AND merged_at IS NOT NULL AND created_at > :since")
    Double getAvgMergeTimeHours(@Param("repoId") UUID repoId, @Param("since") OffsetDateTime since);

    @Query(nativeQuery = true, value =
            "SELECT COUNT(*) FROM pull_requests WHERE repo_id = :repoId AND state = 'OPEN' AND created_at < :before")
    Long countStalePRsByRepo(@Param("repoId") UUID repoId, @Param("before") OffsetDateTime before);

    // ── Commit Trend ─────────────────────────────────────────────────────────

    @Query(nativeQuery = true, value =
            "SELECT DATE_TRUNC(:truncUnit, c.committed_at) AS period, COUNT(*) AS cnt " +
            "FROM commits c JOIN tracked_repos r ON c.repo_id = r.id " +
            "WHERE r.user_id = :userId AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login)) " +
            "AND c.committed_at BETWEEN :from AND :to " +
            "GROUP BY period ORDER BY period ASC")
    List<Object[]> getCommitTrend(@Param("userId") UUID userId, @Param("login") String login,
                                  @Param("truncUnit") String truncUnit,
                                  @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    // ── Overview ─────────────────────────────────────────────────────────────

    @Query(nativeQuery = true, value =
            "SELECT COUNT(*) FROM commits c JOIN tracked_repos r ON c.repo_id = r.id " +
            "WHERE r.user_id = :userId " +
            "AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login)) " +
            "AND c.committed_at BETWEEN :from AND :to")
    Long countCommits(@Param("userId") UUID userId, @Param("login") String login,
                      @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(nativeQuery = true, value =
            "SELECT COUNT(*) FROM pull_requests pr JOIN tracked_repos r ON pr.repo_id = r.id " +
            "WHERE r.user_id = :userId AND (pr.author_login = :login OR (pr.author_login IS NULL AND r.owner = :login)) " +
            "AND pr.created_at BETWEEN :from AND :to")
    Long countPRsAuthored(@Param("userId") UUID userId, @Param("login") String login,
                          @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(nativeQuery = true, value =
            "SELECT COUNT(*) FROM pr_reviews rv " +
            "JOIN pull_requests pr ON rv.pr_id = pr.id " +
            "JOIN tracked_repos r ON pr.repo_id = r.id " +
            "WHERE r.user_id = :userId AND rv.reviewer_login = :login " +
            "AND rv.submitted_at BETWEEN :from AND :to")
    Long countReviewsGiven(@Param("userId") UUID userId, @Param("login") String login,
                           @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(nativeQuery = true, value =
            "SELECT COALESCE(SUM(c.additions), 0), COALESCE(SUM(c.deletions), 0) " +
            "FROM commits c JOIN tracked_repos r ON c.repo_id = r.id " +
            "WHERE r.user_id = :userId " +
            "AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login)) " +
            "AND c.committed_at BETWEEN :from AND :to")
    Object[] getLinesAddedRemoved(@Param("userId") UUID userId, @Param("login") String login,
                                  @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    // ── Insights ─────────────────────────────────────────────────────────────

    @Query(nativeQuery = true, value = """
            SELECT CAST(EXTRACT(DOW FROM c.committed_at AT TIME ZONE :tz) AS int),
                   CAST(EXTRACT(HOUR FROM c.committed_at AT TIME ZONE :tz) AS int),
                   COUNT(*) AS cnt
            FROM commits c JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId
              AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login))
            GROUP BY 1, 2 ORDER BY cnt DESC LIMIT 1
            """)
    Optional<Object[]> getPeakCodingTime(@Param("userId") UUID userId, @Param("login") String login,
                                         @Param("tz") String tz);

    @Query(nativeQuery = true, value = """
            SELECT
              COUNT(*) FILTER (WHERE CAST(EXTRACT(DOW FROM c.committed_at AT TIME ZONE :tz) AS int) BETWEEN 1 AND 5),
              COUNT(*) FILTER (WHERE CAST(EXTRACT(DOW FROM c.committed_at AT TIME ZONE :tz) AS int) IN (0,6))
            FROM commits c JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId
              AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login))
            """)
    Object[] getWeekdayWeekendSplit(@Param("userId") UUID userId, @Param("login") String login,
                                    @Param("tz") String tz);

    @Query(nativeQuery = true, value = """
            SELECT
              COUNT(*) FILTER (WHERE c.committed_at BETWEEN :from AND :to),
              COUNT(*) FILTER (WHERE c.committed_at BETWEEN :prevFrom AND :prevTo)
            FROM commits c JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId
              AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login))
            """)
    Object[] getCommitTrendComparison(@Param("userId") UUID userId, @Param("login") String login,
                                      @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                                      @Param("prevFrom") OffsetDateTime prevFrom, @Param("prevTo") OffsetDateTime prevTo);

    @Query(nativeQuery = true, value =
            "SELECT COUNT(*) FROM pull_requests pr JOIN tracked_repos r ON pr.repo_id = r.id " +
            "WHERE r.user_id = :userId AND pr.state = 'OPEN' AND pr.created_at < :cutoff")
    Long countOpenStalePRs(@Param("userId") UUID userId, @Param("cutoff") OffsetDateTime cutoff);

    @Query(nativeQuery = true, value = """
            SELECT AVG(EXTRACT(EPOCH FROM (pr.merged_at - pr.created_at))/3600)
            FROM pull_requests pr JOIN tracked_repos r ON pr.repo_id = r.id
            WHERE r.user_id = :userId
              AND (pr.author_login = :login OR (pr.author_login IS NULL AND r.owner = :login))
              AND pr.merged_at IS NOT NULL
              AND pr.created_at BETWEEN :from AND :to
            """)
    Double getAvgMergeTimeForUser(@Param("userId") UUID userId, @Param("login") String login,
                                  @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(nativeQuery = true, value = """
            SELECT
              COUNT(*) FILTER (WHERE r.state = 'APPROVED'),
              COUNT(*) FILTER (WHERE r.state = 'CHANGES_REQUESTED'),
              COUNT(*)
            FROM pr_reviews r JOIN pull_requests pr ON r.pr_id = pr.id
            JOIN tracked_repos tr ON pr.repo_id = tr.id
            WHERE tr.user_id = :userId AND r.reviewer_login = :login
              AND r.submitted_at BETWEEN :from AND :to
            """)
    Object[] getReviewStyleStats(@Param("userId") UUID userId, @Param("login") String login,
                                 @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(nativeQuery = true, value =
            "SELECT COUNT(*) FROM commits c JOIN tracked_repos r ON c.repo_id = r.id " +
            "WHERE r.user_id = :userId AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login))")
    Long countTotalCommitsForUser(@Param("userId") UUID userId, @Param("login") String login);

    // ── Streak ───────────────────────────────────────────────────────────────

    @Query(nativeQuery = true, value = """
            WITH daily AS (
              SELECT DISTINCT DATE(c.committed_at AT TIME ZONE :tz) AS day
              FROM commits c JOIN tracked_repos r ON c.repo_id = r.id
              WHERE r.user_id = :userId AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login))
            ),
            gaps AS (
              SELECT day, LAG(day) OVER (ORDER BY day) AS prev_day FROM daily
            ),
            streaks AS (
              SELECT day,
                     SUM(CASE WHEN day - prev_day > 1 OR prev_day IS NULL THEN 1 ELSE 0 END)
                         OVER (ORDER BY day) AS streak_group
              FROM gaps
            ),
            grouped AS (
              SELECT streak_group, MIN(day) AS start_d, MAX(day) AS end_d,
                     MAX(day) - MIN(day) + 1 AS length
              FROM streaks GROUP BY streak_group
            )
            SELECT
              MAX(length) AS longest,
              (SELECT length FROM grouped ORDER BY end_d DESC LIMIT 1) AS current_len,
              (SELECT end_d FROM grouped ORDER BY end_d DESC LIMIT 1) AS last_day
            FROM grouped
            """)
    Object[] getStreakData(@Param("userId") UUID userId, @Param("login") String login,
                           @Param("tz") String tz);

    // ── Public Repo Stats ────────────────────────────────────────────────────

    @Query(nativeQuery = true, value = "SELECT COUNT(*) FROM commits WHERE repo_id = :repoId")
    Long countCommitsByRepo(@Param("repoId") UUID repoId);

    @Query(nativeQuery = true, value =
            "SELECT COUNT(*), COUNT(CASE WHEN merged_at IS NOT NULL THEN 1 END), " +
            "AVG(CASE WHEN merged_at IS NOT NULL THEN EXTRACT(EPOCH FROM (merged_at - created_at))/3600 END) " +
            "FROM pull_requests WHERE repo_id = :repoId")
    Object[] getPRStatsByRepo(@Param("repoId") UUID repoId);

    @Query(nativeQuery = true, value =
            "SELECT author_login, COUNT(*) cnt FROM commits WHERE repo_id = :repoId " +
            "GROUP BY author_login ORDER BY cnt DESC LIMIT 1")
    List<Object[]> getTopContributorByRepo(@Param("repoId") UUID repoId);

    // ── Collaboration ─────────────────────────────────────────────────────────

    @Query(nativeQuery = true, value = """
            SELECT r.reviewer_login, COUNT(*) AS cnt
            FROM pr_reviews r
            JOIN pull_requests pr ON r.pr_id = pr.id
            JOIN tracked_repos tr ON pr.repo_id = tr.id
            WHERE tr.user_id = :userId
              AND pr.author_login = :login
              AND r.reviewer_login <> :login
              AND r.submitted_at BETWEEN :from AND :to
            GROUP BY r.reviewer_login
            ORDER BY cnt DESC
            LIMIT 10
            """)
    List<Object[]> getTopReviewersOfMyPRs(@Param("userId") UUID userId, @Param("login") String login,
                                          @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(nativeQuery = true, value = """
            SELECT pr.author_login, COUNT(*) AS cnt
            FROM pr_reviews r
            JOIN pull_requests pr ON r.pr_id = pr.id
            JOIN tracked_repos tr ON pr.repo_id = tr.id
            WHERE tr.user_id = :userId
              AND r.reviewer_login = :login
              AND pr.author_login <> :login
              AND r.submitted_at BETWEEN :from AND :to
            GROUP BY pr.author_login
            ORDER BY cnt DESC
            LIMIT 10
            """)
    List<Object[]> getTopPeopleIReview(@Param("userId") UUID userId, @Param("login") String login,
                                       @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    // ── Repo Commit Trend ─────────────────────────────────────────────────────

    @Query(nativeQuery = true, value =
            "SELECT DATE_TRUNC('day', c.committed_at) AS day, COUNT(*) AS cnt " +
            "FROM commits c WHERE c.repo_id = :repoId " +
            "AND c.committed_at BETWEEN :from AND :to " +
            "GROUP BY day ORDER BY day ASC")
    List<Object[]> getCommitTrendByRepo(@Param("repoId") UUID repoId,
                                        @Param("from") OffsetDateTime from,
                                        @Param("to") OffsetDateTime to);
}
