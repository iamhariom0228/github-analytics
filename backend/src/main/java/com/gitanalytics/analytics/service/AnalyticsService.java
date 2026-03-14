package com.gitanalytics.analytics.service;

import com.gitanalytics.analytics.dto.*;
import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.ingestion.entity.PullRequest;
import com.gitanalytics.ingestion.repository.CommitRepository;
import com.gitanalytics.ingestion.repository.PullRequestRepository;
import com.gitanalytics.ingestion.repository.PrReviewRepository;
import com.gitanalytics.ingestion.repository.TrackedRepoRepository;
import com.gitanalytics.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    @PersistenceContext
    private EntityManager em;

    private final UserRepository userRepository;
    private final CommitRepository commitRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PrReviewRepository prReviewRepository;
    private final TrackedRepoRepository trackedRepoRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // ---------- Dashboard ----------

    @SuppressWarnings("unchecked")
    public DashboardSummaryDto getDashboard(UUID userId) {
        String cacheKey = "ga:dashboard:" + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof DashboardSummaryDto dto) return dto;

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        OffsetDateTime weekAgo = OffsetDateTime.now().minusWeeks(1);
        OffsetDateTime monthAgo = OffsetDateTime.now().minusMonths(1);

        // Weekly commit count
        long weeklyCommits = countCommits(userId, user.getUsername(), weekAgo, OffsetDateTime.now());

        // Recent PRs
        List<PullRequest> recentPrs = pullRequestRepository.findByUserAndAuthorAndDateRange(
            userId, user.getUsername(), monthAgo, OffsetDateTime.now());

        long mergedCount = recentPrs.stream().filter(p -> p.getMergedAt() != null).count();
        double avgMergeHours = recentPrs.stream()
            .filter(p -> p.getMergedAt() != null && p.getCreatedAt() != null)
            .mapToDouble(p -> Duration.between(p.getCreatedAt(), p.getMergedAt()).toMinutes() / 60.0)
            .average().orElse(0);

        StreakDto streak = getStreak(userId, user.getUsername(), "UTC");

        DashboardSummaryDto dto = DashboardSummaryDto.builder()
            .weeklyCommits(weeklyCommits)
            .monthlyPRsMerged(mergedCount)
            .avgMergeTimeHours(avgMergeHours)
            .currentStreak(streak.getCurrentStreak())
            .recentPRs(recentPrs.stream().limit(5).map(pr -> new PrSummaryDto(
                pr.getId(), pr.getPrNumber(), pr.getTitle(),
                pr.getState().name(), pr.getCreatedAt()
            )).toList())
            .build();

        redisTemplate.opsForValue().set(cacheKey, dto, 5, TimeUnit.MINUTES);
        return dto;
    }

    // ---------- Commit Heatmap ----------

    public List<HeatmapCellDto> getCommitHeatmap(UUID userId, String repoId, String timezone) {
        String tz = timezone != null ? timezone : "UTC";
        String repoFilter = repoId != null ? "AND r.id = :repoId" : "";

        String sql = """
            SELECT CAST(EXTRACT(DOW FROM c.committed_at AT TIME ZONE :tz) AS int) AS day,
                   CAST(EXTRACT(HOUR FROM c.committed_at AT TIME ZONE :tz) AS int) AS hour,
                   CAST(COUNT(*) AS int) AS count
            FROM commits c
            JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId
              AND c.author_login = (SELECT username FROM users WHERE id = :userId)
              """ + repoFilter + """
            GROUP BY day, hour
            ORDER BY day, hour
            """;

        var query = em.createNativeQuery(sql)
            .setParameter("userId", userId)
            .setParameter("tz", tz);
        if (repoId != null) query.setParameter("repoId", UUID.fromString(repoId));

        return ((List<Object[]>) query.getResultList()).stream()
            .map(row -> new HeatmapCellDto(
                ((Number) row[0]).intValue(),
                ((Number) row[1]).intValue(),
                ((Number) row[2]).intValue()
            ))
            .toList();
    }

    // ---------- PR Lifecycle ----------

    public PRLifecycleDto getPRLifecycle(UUID userId, OffsetDateTime from, OffsetDateTime to) {
        String sql = """
            SELECT
              AVG(EXTRACT(EPOCH FROM (pr.first_review_at - pr.created_at))/3600) AS avg_to_review,
              AVG(EXTRACT(EPOCH FROM (pr.merged_at - pr.created_at))/3600) AS avg_to_merge,
              COUNT(*) FILTER (WHERE pr.merged_at IS NOT NULL) AS merged,
              COUNT(*) AS total
            FROM pull_requests pr
            JOIN tracked_repos r ON pr.repo_id = r.id
            WHERE r.user_id = :userId
              AND pr.author_login = (SELECT username FROM users WHERE id = :userId)
              AND pr.created_at BETWEEN :from AND :to
            """;

        Object[] row = (Object[]) em.createNativeQuery(sql)
            .setParameter("userId", userId)
            .setParameter("from", from)
            .setParameter("to", to)
            .getSingleResult();

        return PRLifecycleDto.builder()
            .avgHoursToFirstReview(row[0] != null ? ((Number) row[0]).doubleValue() : 0)
            .avgHoursToMerge(row[1] != null ? ((Number) row[1]).doubleValue() : 0)
            .mergedCount(row[2] != null ? ((Number) row[2]).longValue() : 0)
            .totalCount(row[3] != null ? ((Number) row[3]).longValue() : 0)
            .build();
    }

    // ---------- PR Size Distribution ----------

    public PRSizeDistributionDto getPRSizeDistribution(UUID userId, OffsetDateTime from, OffsetDateTime to) {
        User user = userRepository.findById(userId).orElseThrow();
        List<PullRequest> prs = pullRequestRepository.findByUserAndAuthorAndDateRange(userId, user.getUsername(), from, to);

        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("XS", 0L); // 0-9
        buckets.put("S", 0L);  // 10-49
        buckets.put("M", 0L);  // 50-249
        buckets.put("L", 0L);  // 250-999
        buckets.put("XL", 0L); // 1000+

        for (PullRequest pr : prs) {
            int files = pr.getChangedFiles();
            if (files < 10) buckets.merge("XS", 1L, Long::sum);
            else if (files < 50) buckets.merge("S", 1L, Long::sum);
            else if (files < 250) buckets.merge("M", 1L, Long::sum);
            else if (files < 1000) buckets.merge("L", 1L, Long::sum);
            else buckets.merge("XL", 1L, Long::sum);
        }

        return new PRSizeDistributionDto(buckets);
    }

    // ---------- Team Leaderboard ----------

    @SuppressWarnings("unchecked")
    public List<ContributorStatsDto> getTeamLeaderboard(UUID userId, UUID repoId,
                                                          OffsetDateTime from, OffsetDateTime to) {
        String sql = """
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
            """;

        return ((List<Object[]>) em.createNativeQuery(sql)
            .setParameter("userId", userId)
            .setParameter("repoId", repoId)
            .setParameter("from", from)
            .setParameter("to", to)
            .getResultList()).stream()
            .map(row -> new ContributorStatsDto(
                (String) row[0],
                ((Number) row[1]).longValue(),
                row[2] != null ? ((Number) row[2]).longValue() : 0,
                row[3] != null ? ((Number) row[3]).longValue() : 0
            ))
            .toList();
    }

    // ---------- Bus Factor ----------

    @SuppressWarnings("unchecked")
    public BusFactorDto getBusFactor(UUID userId, UUID repoId) {
        String sql = """
            SELECT c.author_login, COUNT(*) AS commits
            FROM commits c
            JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId AND r.id = :repoId
            GROUP BY c.author_login
            ORDER BY commits DESC
            """;

        List<Object[]> rows = em.createNativeQuery(sql)
            .setParameter("userId", userId)
            .setParameter("repoId", repoId)
            .getResultList();

        if (rows.isEmpty()) return new BusFactorDto(null, 0, 0);

        long total = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        long top = ((Number) rows.get(0)[1]).longValue();
        double pct = total > 0 ? (double) top / total * 100 : 0;

        return new BusFactorDto((String) rows.get(0)[0], pct, (int) rows.size());
    }

    // ---------- Contribution Streak ----------

    public StreakDto getStreak(UUID userId, String login, String timezone) {
        String cacheKey = "ga:streak:" + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof StreakDto dto) return dto;

        String sql = """
            WITH daily AS (
              SELECT DISTINCT DATE(c.committed_at AT TIME ZONE :tz) AS day
              FROM commits c JOIN tracked_repos r ON c.repo_id = r.id
              WHERE r.user_id = :userId AND c.author_login = :login
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
            """;

        try {
            Object[] row = (Object[]) em.createNativeQuery(sql)
                .setParameter("userId", userId)
                .setParameter("login", login)
                .setParameter("tz", timezone)
                .getSingleResult();

            int longest = row[0] != null ? ((Number) row[0]).intValue() : 0;
            int current = 0;
            if (row[1] != null && row[2] != null) {
                LocalDate lastDay = ((java.sql.Date) row[2]).toLocalDate();
                LocalDate today = LocalDate.now(ZoneId.of(timezone));
                // Streak is active if last commit was today or yesterday
                if (!lastDay.isBefore(today.minusDays(1))) {
                    current = ((Number) row[1]).intValue();
                }
            }

            StreakDto dto = new StreakDto(current, longest);
            redisTemplate.opsForValue().set(cacheKey, dto, 1, TimeUnit.HOURS);
            return dto;
        } catch (Exception e) {
            log.debug("Streak query returned no data: {}", e.getMessage());
            return new StreakDto(0, 0);
        }
    }

    // ---------- Reviews Summary ----------

    public ReviewsSummaryDto getReviewsSummary(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        String sql = """
            SELECT
              COUNT(*) AS total,
              COUNT(*) FILTER (WHERE r.state = 'APPROVED') AS approved,
              COUNT(*) FILTER (WHERE r.state = 'CHANGES_REQUESTED') AS changes_requested,
              COUNT(*) FILTER (WHERE r.state = 'COMMENTED') AS commented,
              COUNT(DISTINCT r.pr_id) AS prs_reviewed
            FROM pr_reviews r
            JOIN pull_requests pr ON r.pr_id = pr.id
            JOIN tracked_repos tr ON pr.repo_id = tr.id
            WHERE tr.user_id = :userId
              AND r.reviewer_login = :login
              AND r.submitted_at BETWEEN :from AND :to
            """;

        Object[] row = (Object[]) em.createNativeQuery(sql)
            .setParameter("userId", userId)
            .setParameter("login", login)
            .setParameter("from", from)
            .setParameter("to", to)
            .getSingleResult();

        long total = row[0] != null ? ((Number) row[0]).longValue() : 0;
        long approved = row[1] != null ? ((Number) row[1]).longValue() : 0;
        long changesRequested = row[2] != null ? ((Number) row[2]).longValue() : 0;
        long commented = row[3] != null ? ((Number) row[3]).longValue() : 0;
        long prsReviewed = row[4] != null ? ((Number) row[4]).longValue() : 0;
        double avg = prsReviewed > 0 ? (double) total / prsReviewed : 0;

        return new ReviewsSummaryDto(total, approved, changesRequested, commented, avg);
    }

    // ---------- Stale PRs ----------

    public List<PrSummaryDto> getStalePRs(UUID userId, UUID repoId, int olderThanDays) {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(olderThanDays);
        return pullRequestRepository.findStalePRs(repoId, threshold).stream()
            .map(pr -> new PrSummaryDto(pr.getId(), pr.getPrNumber(), pr.getTitle(),
                pr.getState().name(), pr.getCreatedAt()))
            .toList();
    }

    // ---------- Private Helpers ----------

    private long countCommits(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        String sql = "SELECT COUNT(*) FROM commits c JOIN tracked_repos r ON c.repo_id = r.id " +
                     "WHERE r.user_id = :userId AND c.author_login = :login " +
                     "AND c.committed_at BETWEEN :from AND :to";
        return ((Number) em.createNativeQuery(sql)
            .setParameter("userId", userId)
            .setParameter("login", login)
            .setParameter("from", from)
            .setParameter("to", to)
            .getSingleResult()).longValue();
    }
}
