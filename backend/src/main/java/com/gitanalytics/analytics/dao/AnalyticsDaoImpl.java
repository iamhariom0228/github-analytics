package com.gitanalytics.analytics.dao;

import com.gitanalytics.analytics.dto.CommitTrendDto;
import com.gitanalytics.analytics.dto.ContributorStatsDto;
import com.gitanalytics.analytics.dto.HeatmapCellDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class AnalyticsDaoImpl implements AnalyticsDao {

    @PersistenceContext
    private EntityManager em;

    // ── Heatmap ──────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public List<HeatmapCellDto> getCommitHeatmap(UUID userId, String timezone, UUID repoId,
                                                   OffsetDateTime from, OffsetDateTime to) {
        String sql = """
            SELECT CAST(EXTRACT(DOW FROM c.committed_at AT TIME ZONE :tz) AS int) AS day,
                   CAST(EXTRACT(HOUR FROM c.committed_at AT TIME ZONE :tz) AS int) AS hour,
                   CAST(COUNT(*) AS int) AS count
            FROM commits c
            JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId
              AND c.author_login = (SELECT username FROM users WHERE id = :userId)
            """
            + (repoId != null ? "AND r.id = :repoId\n" : "")
            + (from != null && to != null ? "AND c.committed_at BETWEEN :from AND :to\n" : "")
            + """
            GROUP BY day, hour
            ORDER BY day, hour
            """;

        var query = em.createNativeQuery(sql)
            .setParameter("userId", userId)
            .setParameter("tz", timezone != null ? timezone : "UTC");
        if (repoId != null) query.setParameter("repoId", repoId);
        if (from != null && to != null) {
            query.setParameter("from", from);
            query.setParameter("to", to);
        }
        return ((List<Object[]>) query.getResultList()).stream()
            .map(row -> new HeatmapCellDto(
                ((Number) row[0]).intValue(),
                ((Number) row[1]).intValue(),
                ((Number) row[2]).intValue()))
            .toList();
    }

    // ── PR Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public Object[] getPRLifecycleStats(UUID userId, OffsetDateTime from, OffsetDateTime to) {
        return (Object[]) em.createNativeQuery("""
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
            .setParameter("userId", userId)
            .setParameter("from", from)
            .setParameter("to", to)
            .getSingleResult();
    }

    // ── Team Leaderboard ─────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public List<ContributorStatsDto> getTeamLeaderboard(UUID userId, UUID repoId,
                                                         OffsetDateTime from, OffsetDateTime to) {
        return ((List<Object[]>) em.createNativeQuery("""
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
            .setParameter("userId", userId)
            .setParameter("repoId", repoId)
            .setParameter("from", from)
            .setParameter("to", to)
            .getResultList()).stream()
            .map(row -> new ContributorStatsDto(
                (String) row[0],
                ((Number) row[1]).longValue(),
                row[2] != null ? ((Number) row[2]).longValue() : 0,
                row[3] != null ? ((Number) row[3]).longValue() : 0))
            .toList();
    }

    // ── Bus Factor ───────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> getBusFactorRows(UUID userId, UUID repoId) {
        return em.createNativeQuery("""
            SELECT c.author_login, COUNT(*) AS commits
            FROM commits c
            JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId AND r.id = :repoId
            GROUP BY c.author_login
            ORDER BY commits DESC
            """)
            .setParameter("userId", userId)
            .setParameter("repoId", repoId)
            .getResultList();
    }

    // ── Reviews Summary ──────────────────────────────────────────────────────

    @Override
    public Object[] getReviewsSummaryRow(UUID userId, String login,
                                          OffsetDateTime from, OffsetDateTime to) {
        return (Object[]) em.createNativeQuery("""
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
            .setParameter("userId", userId)
            .setParameter("login", login)
            .setParameter("from", from)
            .setParameter("to", to)
            .getSingleResult();
    }

    // ── Repo Health ──────────────────────────────────────────────────────────

    @Override
    public long countRecentCommits(UUID repoId, OffsetDateTime since) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM commits WHERE repo_id = :repoId AND committed_at > :since")
            .setParameter("repoId", repoId).setParameter("since", since)
            .getSingleResult()).longValue();
    }

    @Override
    public Object[] getPRReviewCoverage(UUID repoId, OffsetDateTime since) {
        return (Object[]) em.createNativeQuery(
                "SELECT COUNT(DISTINCT pr.id), COUNT(DISTINCT rv.pr_id) " +
                "FROM pull_requests pr LEFT JOIN pr_reviews rv ON rv.pr_id = pr.id " +
                "WHERE pr.repo_id = :repoId AND pr.created_at > :since")
            .setParameter("repoId", repoId).setParameter("since", since)
            .getSingleResult();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> getTopAuthorRows(UUID repoId, OffsetDateTime since) {
        return em.createNativeQuery(
                "SELECT COALESCE(author_login, 'unknown'), COUNT(*) AS cnt FROM commits " +
                "WHERE repo_id = :repoId AND committed_at > :since " +
                "GROUP BY author_login ORDER BY cnt DESC LIMIT 1")
            .setParameter("repoId", repoId).setParameter("since", since)
            .getResultList();
    }

    @Override
    public Double getAvgMergeTimeHours(UUID repoId, OffsetDateTime since) {
        Object result = em.createNativeQuery(
                "SELECT AVG(EXTRACT(EPOCH FROM (merged_at - created_at))/3600) " +
                "FROM pull_requests WHERE repo_id = :repoId AND merged_at IS NOT NULL AND created_at > :since")
            .setParameter("repoId", repoId).setParameter("since", since)
            .getSingleResult();
        return result != null ? ((Number) result).doubleValue() : null;
    }

    @Override
    public long countStalePRsByRepo(UUID repoId, OffsetDateTime before) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM pull_requests WHERE repo_id = :repoId AND state = 'OPEN' AND created_at < :cutoff")
            .setParameter("repoId", repoId).setParameter("cutoff", before)
            .getSingleResult()).longValue();
    }

    // ── Commit Trend ─────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public List<CommitTrendDto> getCommitTrend(UUID userId, String login, String granularity,
                                                OffsetDateTime from, OffsetDateTime to) {
        String truncFn = "week".equalsIgnoreCase(granularity) ? "week" : "day";
        List<Object[]> rows = em.createNativeQuery(
                "SELECT DATE_TRUNC('" + truncFn + "', c.committed_at) AS period, COUNT(*) AS cnt " +
                "FROM commits c JOIN tracked_repos r ON c.repo_id = r.id " +
                "WHERE r.user_id = :userId AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login)) " +
                "AND c.committed_at BETWEEN :from AND :to " +
                "GROUP BY period ORDER BY period ASC")
            .setParameter("userId", userId).setParameter("login", login)
            .setParameter("from", from).setParameter("to", to)
            .getResultList();
        return rows.stream().map(r -> {
            String date;
            if (r[0] instanceof java.sql.Timestamp ts)
                date = ts.toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString();
            else if (r[0] instanceof java.time.Instant inst)
                date = inst.atOffset(ZoneOffset.UTC).toLocalDate().toString();
            else
                date = r[0].toString().substring(0, 10);
            return new CommitTrendDto(date, ((Number) r[1]).longValue());
        }).toList();
    }

    // ── Overview ─────────────────────────────────────────────────────────────

    @Override
    public long countCommits(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM commits c JOIN tracked_repos r ON c.repo_id = r.id " +
                "WHERE r.user_id = :userId " +
                "AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login)) " +
                "AND c.committed_at BETWEEN :from AND :to")
            .setParameter("userId", userId).setParameter("login", login)
            .setParameter("from", from).setParameter("to", to)
            .getSingleResult()).longValue();
    }

    @Override
    public long countPRsAuthored(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM pull_requests pr JOIN tracked_repos r ON pr.repo_id = r.id " +
                "WHERE r.user_id = :userId AND (pr.author_login = :login OR (pr.author_login IS NULL AND r.owner = :login)) " +
                "AND pr.created_at BETWEEN :from AND :to")
            .setParameter("userId", userId).setParameter("login", login)
            .setParameter("from", from).setParameter("to", to)
            .getSingleResult()).longValue();
    }

    @Override
    public long countReviewsGiven(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM pr_reviews rv " +
                "JOIN pull_requests pr ON rv.pr_id = pr.id " +
                "JOIN tracked_repos r ON pr.repo_id = r.id " +
                "WHERE r.user_id = :userId AND rv.reviewer_login = :login " +
                "AND rv.submitted_at BETWEEN :from AND :to")
            .setParameter("userId", userId).setParameter("login", login)
            .setParameter("from", from).setParameter("to", to)
            .getSingleResult()).longValue();
    }

    @Override
    public Object[] getLinesAddedRemoved(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        return (Object[]) em.createNativeQuery(
                "SELECT COALESCE(SUM(c.additions), 0), COALESCE(SUM(c.deletions), 0) " +
                "FROM commits c JOIN tracked_repos r ON c.repo_id = r.id " +
                "WHERE r.user_id = :userId " +
                "AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login)) " +
                "AND c.committed_at BETWEEN :from AND :to")
            .setParameter("userId", userId).setParameter("login", login)
            .setParameter("from", from).setParameter("to", to)
            .getSingleResult();
    }

    // ── Insights ─────────────────────────────────────────────────────────────

    @Override
    public Object[] getPeakCodingTime(UUID userId, String login, String timezone) {
        return (Object[]) em.createNativeQuery("""
            SELECT CAST(EXTRACT(DOW FROM c.committed_at AT TIME ZONE :tz) AS int),
                   CAST(EXTRACT(HOUR FROM c.committed_at AT TIME ZONE :tz) AS int),
                   COUNT(*) AS cnt
            FROM commits c JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId
              AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login))
            GROUP BY 1, 2 ORDER BY cnt DESC LIMIT 1
            """)
            .setParameter("userId", userId).setParameter("login", login).setParameter("tz", timezone)
            .getSingleResult();
    }

    @Override
    public Object[] getWeekdayWeekendSplit(UUID userId, String login, String timezone) {
        return (Object[]) em.createNativeQuery("""
            SELECT
              COUNT(*) FILTER (WHERE CAST(EXTRACT(DOW FROM c.committed_at AT TIME ZONE :tz) AS int) BETWEEN 1 AND 5),
              COUNT(*) FILTER (WHERE CAST(EXTRACT(DOW FROM c.committed_at AT TIME ZONE :tz) AS int) IN (0,6))
            FROM commits c JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId
              AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login))
            """)
            .setParameter("userId", userId).setParameter("login", login).setParameter("tz", timezone)
            .getSingleResult();
    }

    @Override
    public Object[] getCommitTrendComparison(UUID userId, String login,
                                              OffsetDateTime from, OffsetDateTime to,
                                              OffsetDateTime prevFrom, OffsetDateTime prevTo) {
        return (Object[]) em.createNativeQuery("""
            SELECT
              COUNT(*) FILTER (WHERE c.committed_at BETWEEN :from AND :to),
              COUNT(*) FILTER (WHERE c.committed_at BETWEEN :prevFrom AND :prevTo)
            FROM commits c JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId
              AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login))
            """)
            .setParameter("userId", userId).setParameter("login", login)
            .setParameter("from", from).setParameter("to", to)
            .setParameter("prevFrom", prevFrom).setParameter("prevTo", prevTo)
            .getSingleResult();
    }

    @Override
    public long countOpenStalePRsForUser(UUID userId) {
        return ((Number) em.createNativeQuery("""
            SELECT COUNT(*) FROM pull_requests pr
            JOIN tracked_repos r ON pr.repo_id = r.id
            WHERE r.user_id = :userId AND pr.state = 'OPEN'
              AND pr.created_at < NOW() - INTERVAL '7 days'
            """)
            .setParameter("userId", userId)
            .getSingleResult()).longValue();
    }

    @Override
    public Double getAvgMergeTimeForUser(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        Object result = em.createNativeQuery("""
            SELECT AVG(EXTRACT(EPOCH FROM (pr.merged_at - pr.created_at))/3600)
            FROM pull_requests pr JOIN tracked_repos r ON pr.repo_id = r.id
            WHERE r.user_id = :userId
              AND (pr.author_login = :login OR (pr.author_login IS NULL AND r.owner = :login))
              AND pr.merged_at IS NOT NULL
              AND pr.created_at BETWEEN :from AND :to
            """)
            .setParameter("userId", userId).setParameter("login", login)
            .setParameter("from", from).setParameter("to", to)
            .getSingleResult();
        return result != null ? ((Number) result).doubleValue() : null;
    }

    @Override
    public Object[] getReviewStyleStats(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        return (Object[]) em.createNativeQuery("""
            SELECT
              COUNT(*) FILTER (WHERE r.state = 'APPROVED'),
              COUNT(*) FILTER (WHERE r.state = 'CHANGES_REQUESTED'),
              COUNT(*)
            FROM pr_reviews r JOIN pull_requests pr ON r.pr_id = pr.id
            JOIN tracked_repos tr ON pr.repo_id = tr.id
            WHERE tr.user_id = :userId AND r.reviewer_login = :login
              AND r.submitted_at BETWEEN :from AND :to
            """)
            .setParameter("userId", userId).setParameter("login", login)
            .setParameter("from", from).setParameter("to", to)
            .getSingleResult();
    }

    @Override
    public long countTotalCommitsForUser(UUID userId, String login) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM commits c JOIN tracked_repos r ON c.repo_id = r.id " +
                "WHERE r.user_id = :userId AND (c.author_login = :login OR (c.author_login IS NULL AND r.owner = :login))")
            .setParameter("userId", userId).setParameter("login", login)
            .getSingleResult()).longValue();
    }

    // ── Streak ───────────────────────────────────────────────────────────────

    @Override
    public Object[] getStreakData(UUID userId, String login, String timezone) {
        return (Object[]) em.createNativeQuery("""
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
            .setParameter("userId", userId).setParameter("login", login).setParameter("tz", timezone)
            .getSingleResult();
    }

    // ── Public Repo Stats ────────────────────────────────────────────────────

    @Override
    public long countCommitsByRepo(UUID repoId) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM commits WHERE repo_id = :repoId")
            .setParameter("repoId", repoId).getSingleResult()).longValue();
    }

    @Override
    public Object[] getPRStatsByRepo(UUID repoId) {
        return (Object[]) em.createNativeQuery(
                "SELECT COUNT(*), COUNT(CASE WHEN merged_at IS NOT NULL THEN 1 END), " +
                "AVG(CASE WHEN merged_at IS NOT NULL THEN EXTRACT(EPOCH FROM (merged_at - created_at))/3600 END) " +
                "FROM pull_requests WHERE repo_id = :repoId")
            .setParameter("repoId", repoId).getSingleResult();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> getTopContributorByRepo(UUID repoId) {
        return em.createNativeQuery(
                "SELECT author_login, COUNT(*) cnt FROM commits WHERE repo_id = :repoId " +
                "GROUP BY author_login ORDER BY cnt DESC LIMIT 1")
            .setParameter("repoId", repoId).getResultList();
    }

    // ── AI Summary ───────────────────────────────────────────────────────────

    @Override
    public long countOpenStalePRsOlderThan(UUID userId, String intervalDays) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM pull_requests pr JOIN tracked_repos r ON pr.repo_id = r.id " +
                "WHERE r.user_id = :userId AND pr.state = 'OPEN' " +
                "AND pr.created_at < NOW() - INTERVAL '" + intervalDays + " days'")
            .setParameter("userId", userId).getSingleResult()).longValue();
    }
}
