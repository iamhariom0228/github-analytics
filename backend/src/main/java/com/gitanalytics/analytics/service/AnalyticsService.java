package com.gitanalytics.analytics.service;

import com.gitanalytics.analytics.dto.*;
import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.ingestion.entity.PullRequest;
import com.gitanalytics.ingestion.repository.CommitRepository;
import com.gitanalytics.ingestion.repository.PullRequestRepository;
import com.gitanalytics.ingestion.repository.PrReviewRepository;
import com.gitanalytics.ingestion.repository.TrackedRepoRepository;
import com.gitanalytics.shared.client.GroqApiClient;
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
    private final GroqApiClient groqApiClient;

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

    public List<HeatmapCellDto> getCommitHeatmap(UUID userId, String repoId, String timezone,
                                                   OffsetDateTime from, OffsetDateTime to) {
        String tz = timezone != null ? timezone : "UTC";
        String repoFilter = repoId != null ? "AND r.id = :repoId" : "";
        String dateFilter = (from != null && to != null) ? "AND c.committed_at BETWEEN :from AND :to" : "";

        String sql = """
            SELECT CAST(EXTRACT(DOW FROM c.committed_at AT TIME ZONE :tz) AS int) AS day,
                   CAST(EXTRACT(HOUR FROM c.committed_at AT TIME ZONE :tz) AS int) AS hour,
                   CAST(COUNT(*) AS int) AS count
            FROM commits c
            JOIN tracked_repos r ON c.repo_id = r.id
            WHERE r.user_id = :userId
              AND c.author_login = (SELECT username FROM users WHERE id = :userId)
              """ + repoFilter + " " + dateFilter + """

            GROUP BY day, hour
            ORDER BY day, hour
            """;

        var query = em.createNativeQuery(sql)
            .setParameter("userId", userId)
            .setParameter("tz", tz);
        if (repoId != null) query.setParameter("repoId", UUID.fromString(repoId));
        if (from != null && to != null) {
            query.setParameter("from", from);
            query.setParameter("to", to);
        }

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

    // ---------- Insights ----------

    @SuppressWarnings("unchecked")
    public List<InsightDto> getInsights(UUID userId, String login, String timezone,
                                        OffsetDateTime from, OffsetDateTime to) {
        List<InsightDto> insights = new ArrayList<>();
        String tz = timezone != null ? timezone : "UTC";

        // Use provided window, or default to last 30 days
        OffsetDateTime effectiveTo = to != null ? to : OffsetDateTime.now();
        OffsetDateTime effectiveFrom = from != null ? from : effectiveTo.minusDays(30);
        long windowDays = java.time.Duration.between(effectiveFrom, effectiveTo).toDays();
        String periodLabel = windowDays <= 7 ? "Last 7 Days" : windowDays <= 31 ? "Last 30 Days" :
                             windowDays <= 93 ? "Last 90 Days" : "Selected Period";

        // 1. Most productive day + hour (always all-time — behavioral pattern, not activity metric)
        try {
            Object[] peak = (Object[]) em.createNativeQuery("""
                SELECT CAST(EXTRACT(DOW FROM c.committed_at AT TIME ZONE :tz) AS int) AS day,
                       CAST(EXTRACT(HOUR FROM c.committed_at AT TIME ZONE :tz) AS int) AS hour,
                       COUNT(*) AS cnt
                FROM commits c JOIN tracked_repos r ON c.repo_id = r.id
                WHERE r.user_id = :userId AND c.author_login = :login
                GROUP BY day, hour ORDER BY cnt DESC LIMIT 1
                """)
                .setParameter("userId", userId).setParameter("login", login).setParameter("tz", tz)
                .getSingleResult();
            String[] dayNames = {"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
            String[] fullDayNames = {"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"};
            int day = ((Number) peak[0]).intValue();
            int hour = ((Number) peak[1]).intValue();
            String ampm = hour < 12 ? (hour == 0 ? "12am" : hour + "am") : (hour == 12 ? "12pm" : (hour - 12) + "pm");
            insights.add(new InsightDto(
                "You're most productive on " + fullDayNames[day] + "s around " + String.format("%02d:00", hour) + ".",
                "info",
                dayNames[day] + " " + ampm,
                "Peak Coding Time"
            ));
        } catch (Exception ignored) {}

        // 2. Weekday vs weekend (always all-time — behavioral pattern)
        try {
            Object[] ww = (Object[]) em.createNativeQuery("""
                SELECT
                  COUNT(*) FILTER (WHERE CAST(EXTRACT(DOW FROM c.committed_at AT TIME ZONE :tz) AS int) BETWEEN 1 AND 5),
                  COUNT(*) FILTER (WHERE CAST(EXTRACT(DOW FROM c.committed_at AT TIME ZONE :tz) AS int) IN (0,6))
                FROM commits c JOIN tracked_repos r ON c.repo_id = r.id
                WHERE r.user_id = :userId AND c.author_login = :login
                """)
                .setParameter("userId", userId).setParameter("login", login).setParameter("tz", tz)
                .getSingleResult();
            long weekday = ((Number) ww[0]).longValue();
            long weekend = ((Number) ww[1]).longValue();
            if (weekday + weekend > 0) {
                int weekendPct = (int) Math.round((double) weekend / (weekday + weekend) * 100);
                int weekdayPct = 100 - weekendPct;
                if (weekendPct >= 30) {
                    insights.add(new InsightDto(
                        "You often code on weekends — " + weekendPct + "% of your commits happen on Sat/Sun.",
                        "info",
                        weekendPct + "%",
                        "Weekend Commits"
                    ));
                } else {
                    insights.add(new InsightDto(
                        "You're a weekday coder — " + weekdayPct + "% of your commits happen Mon–Fri.",
                        "info",
                        weekdayPct + "%",
                        "Weekday Commits"
                    ));
                }
            }
        } catch (Exception ignored) {}

        // 3. Commit trend: selected window vs equivalent previous window
        try {
            OffsetDateTime prevFrom = effectiveFrom.minusDays(windowDays);
            Object[] trend = (Object[]) em.createNativeQuery("""
                SELECT
                  COUNT(*) FILTER (WHERE c.committed_at BETWEEN :from AND :to),
                  COUNT(*) FILTER (WHERE c.committed_at BETWEEN :prevFrom AND :prevTo)
                FROM commits c JOIN tracked_repos r ON c.repo_id = r.id
                WHERE r.user_id = :userId AND c.author_login = :login
                """)
                .setParameter("userId", userId).setParameter("login", login)
                .setParameter("from", effectiveFrom).setParameter("to", effectiveTo)
                .setParameter("prevFrom", prevFrom).setParameter("prevTo", effectiveFrom)
                .getSingleResult();
            long current = ((Number) trend[0]).longValue();
            long previous = ((Number) trend[1]).longValue();
            if (previous > 0) {
                long changePct = Math.round((double)(current - previous) / previous * 100);
                if (changePct >= 20) {
                    insights.add(new InsightDto(
                        "Your commit activity is up " + changePct + "% vs the previous " + windowDays + " days.",
                        "positive",
                        "+" + changePct + "%",
                        "vs Prev Period"
                    ));
                } else if (changePct <= -20) {
                    insights.add(new InsightDto(
                        "Your commit activity dropped " + Math.abs(changePct) + "% vs the previous " + windowDays + " days.",
                        "warning",
                        changePct + "%",
                        "vs Prev Period"
                    ));
                } else {
                    insights.add(new InsightDto(
                        "Your commit pace is steady — " + current + " commits this period vs " + previous + " previously.",
                        "info",
                        current + " commits",
                        periodLabel
                    ));
                }
            } else if (current > 0) {
                insights.add(new InsightDto(
                    "You've made " + current + " commits in this period — great momentum!",
                    "positive",
                    String.valueOf(current),
                    periodLabel
                ));
            }
        } catch (Exception ignored) {}

        // 4. Stale PRs — always current state, date range doesn't apply
        try {
            long stale = ((Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM pull_requests pr
                JOIN tracked_repos r ON pr.repo_id = r.id
                WHERE r.user_id = :userId AND pr.state = 'OPEN'
                  AND pr.created_at < NOW() - INTERVAL '7 days'
                """)
                .setParameter("userId", userId)
                .getSingleResult()).longValue();
            if (stale > 0) {
                insights.add(new InsightDto(
                    stale + " PR" + (stale > 1 ? "s have" : " has") + " been open for over 7 days without being merged.",
                    "warning",
                    String.valueOf(stale),
                    "Stale PRs"
                ));
            }
        } catch (Exception ignored) {}

        // 5. PR merge time — use selected window
        try {
            Object raw = em.createNativeQuery("""
                SELECT AVG(EXTRACT(EPOCH FROM (merged_at - created_at))/3600)
                FROM pull_requests pr JOIN tracked_repos r ON pr.repo_id = r.id
                WHERE r.user_id = :userId AND pr.author_login = :login
                  AND pr.merged_at IS NOT NULL
                  AND pr.created_at BETWEEN :from AND :to
                """)
                .setParameter("userId", userId).setParameter("login", login)
                .setParameter("from", effectiveFrom).setParameter("to", effectiveTo)
                .getSingleResult();
            if (raw != null) {
                double avgHours = ((Number) raw).doubleValue();
                String metricVal = avgHours < 1 ? Math.round(avgHours * 60) + "m" : String.format("%.0fh", avgHours);
                if (avgHours < 4) {
                    insights.add(new InsightDto(
                        "Your PRs are merged in under 4 hours on average — excellent turnaround!",
                        "positive",
                        metricVal,
                        "Avg Merge Time"
                    ));
                } else if (avgHours > 48) {
                    insights.add(new InsightDto(
                        "Consider breaking PRs into smaller chunks for faster reviews.",
                        "warning",
                        metricVal,
                        "Avg Merge Time"
                    ));
                } else {
                    insights.add(new InsightDto(
                        "Your average PR merge time is " + metricVal + ".",
                        "info",
                        metricVal,
                        "Avg Merge Time"
                    ));
                }
            }
        } catch (Exception ignored) {}

        // 6. Review style — use selected window
        try {
            Object[] rv = (Object[]) em.createNativeQuery("""
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
                .setParameter("from", effectiveFrom).setParameter("to", effectiveTo)
                .getSingleResult();
            long approved = ((Number) rv[0]).longValue();
            long changesRequested = ((Number) rv[1]).longValue();
            long total = ((Number) rv[2]).longValue();
            if (total >= 5) {
                int approvePct = (int) Math.round((double) approved / total * 100);
                if (approvePct >= 80) {
                    insights.add(new InsightDto(
                        "You approve " + approvePct + "% of PRs you review — you're a collaborative reviewer.",
                        "positive",
                        approvePct + "%",
                        "Approval Rate"
                    ));
                } else if (changesRequested > approved) {
                    insights.add(new InsightDto(
                        "You request changes more often than you approve — you hold the bar high.",
                        "info",
                        approvePct + "%",
                        "Approval Rate"
                    ));
                }
            }
        } catch (Exception ignored) {}

        if (insights.isEmpty()) {
            try {
                Object totalRaw = em.createNativeQuery("""
                    SELECT COUNT(*) FROM commits c JOIN tracked_repos r ON c.repo_id = r.id
                    WHERE r.user_id = :userId AND c.author_login = :login
                    """)
                    .setParameter("userId", userId).setParameter("login", login)
                    .getSingleResult();
                long total = ((Number) totalRaw).longValue();
                if (total > 0) {
                    insights.add(new InsightDto(
                        "You have " + total + " commits tracked across your repositories.",
                        "info",
                        String.valueOf(total),
                        "Total Commits"
                    ));
                } else {
                    insights.add(new InsightDto(
                        "Sync a repository to start seeing personalized insights.",
                        "info"
                    ));
                }
            } catch (Exception ignored) {
                insights.add(new InsightDto(
                    "Sync a repository to start seeing personalized insights.",
                    "info"
                ));
            }
        }

        // AI-generated insight via Groq (no-op if key not set)
        if (!insights.isEmpty()) {
            try {
                String summary = insights.stream()
                    .map(InsightDto::message)
                    .collect(java.util.stream.Collectors.joining("; "));
                String aiText = groqApiClient.complete(
                    "You are a developer productivity coach. Given metrics about a developer, write one encouraging, specific insight in 1-2 sentences. Be direct and concrete. No fluff.",
                    "Developer stats: " + summary + ". Give one actionable insight."
                );
                if (aiText != null && !aiText.isBlank()) {
                    insights.add(new InsightDto(aiText, "info", null, "AI Insight"));
                }
            } catch (Exception ignored) {}
        }

        return insights;
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

    // ---------- Repo Health Score ----------

    public RepoHealthDto getRepoHealth(UUID userId, UUID repoId) {
        trackedRepoRepository.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime thirtyDaysAgo = now.minusDays(30);
        OffsetDateTime fourteenDaysAgo = now.minusDays(14);

        List<RepoHealthDto.SignalDto> signals = new ArrayList<>();
        int passed = 0;

        // Signal 1: commit velocity — always scored, no commits = inactive
        long recentCommits = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM commits WHERE repo_id = :repoId AND committed_at > :since")
            .setParameter("repoId", repoId).setParameter("since", thirtyDaysAgo)
            .getSingleResult()).longValue();
        boolean active = recentCommits >= 5;
        if (active) passed++;
        signals.add(new RepoHealthDto.SignalDto("Commit velocity (30d)", active,
            recentCommits == 0 ? "No commits in 30 days" :
            active ? recentCommits + " commits" : recentCommits + " commits — needs 5+"));

        // Signal 2: PR review coverage — no PRs = fail (no review culture)
        Object[] reviewCoverage = (Object[]) em.createNativeQuery(
                "SELECT COUNT(DISTINCT pr.id), COUNT(DISTINCT rv.pr_id) " +
                "FROM pull_requests pr LEFT JOIN pr_reviews rv ON rv.pr_id = pr.id " +
                "WHERE pr.repo_id = :repoId AND pr.created_at > :since")
            .setParameter("repoId", repoId).setParameter("since", thirtyDaysAgo)
            .getSingleResult();
        long totalPRs30d = ((Number) reviewCoverage[0]).longValue();
        long reviewedPRs = ((Number) reviewCoverage[1]).longValue();
        boolean goodCoverage = totalPRs30d > 0 && (reviewedPRs * 100 / totalPRs30d) >= 50;
        if (goodCoverage) passed++;
        signals.add(new RepoHealthDto.SignalDto("PR review coverage", goodCoverage,
            totalPRs30d == 0 ? "No PRs opened in 30 days" :
            reviewedPRs + "/" + totalPRs30d + " PRs reviewed"));

        // Signal 3: bus factor — top contributor owns < 70%; no commits = fail
        if (recentCommits > 0) {
            List<Object[]> topAuthors = (List<Object[]>) em.createNativeQuery(
                    "SELECT COALESCE(author_login, 'unknown'), COUNT(*) AS cnt FROM commits " +
                    "WHERE repo_id = :repoId AND committed_at > :since " +
                    "GROUP BY author_login ORDER BY cnt DESC LIMIT 1")
                .setParameter("repoId", repoId).setParameter("since", thirtyDaysAgo)
                .getResultList();
            if (!topAuthors.isEmpty()) {
                Object[] top = topAuthors.get(0);
                long topCount = ((Number) top[1]).longValue();
                int topPct = (int) Math.round((double) topCount / recentCommits * 100);
                boolean goodBusFactor = topPct < 70;
                if (goodBusFactor) passed++;
                signals.add(new RepoHealthDto.SignalDto("Bus factor", goodBusFactor,
                    top[0] + " owns " + topPct + "% of commits"));
            } else {
                signals.add(new RepoHealthDto.SignalDto("Bus factor", false, "Unresolved commit authors"));
            }
        } else {
            signals.add(new RepoHealthDto.SignalDto("Bus factor", false, "No recent commits to evaluate"));
        }

        // Signal 4: avg PR merge time — no merged PRs = pass (can't penalise absent data)
        Object avgMergeRaw = em.createNativeQuery(
                "SELECT AVG(EXTRACT(EPOCH FROM (merged_at - created_at))/3600) " +
                "FROM pull_requests WHERE repo_id = :repoId AND merged_at IS NOT NULL AND created_at > :since")
            .setParameter("repoId", repoId).setParameter("since", thirtyDaysAgo)
            .getSingleResult();
        boolean fastMerge = avgMergeRaw == null || ((Number) avgMergeRaw).doubleValue() < 48;
        if (fastMerge) passed++;
        signals.add(new RepoHealthDto.SignalDto("PR merge time", fastMerge,
            avgMergeRaw == null ? "No merged PRs" :
            String.format("%.1fh avg", ((Number) avgMergeRaw).doubleValue())));

        // Signal 5: no stale PRs — no open PRs = pass
        long stalePRs = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM pull_requests WHERE repo_id = :repoId AND state = 'OPEN' AND created_at < :cutoff")
            .setParameter("repoId", repoId).setParameter("cutoff", fourteenDaysAgo)
            .getSingleResult()).longValue();
        boolean noStale = stalePRs == 0;
        if (noStale) passed++;
        signals.add(new RepoHealthDto.SignalDto("No stale PRs (>14d)", noStale,
            noStale ? "All clear" : stalePRs + " stale PR" + (stalePRs != 1 ? "s" : "")));

        // Score always out of 5
        int score = passed * 20;
        String label = score >= 80 ? "Healthy" : score >= 60 ? "At Risk" : "Needs Attention";
        return new RepoHealthDto(score, label, signals);
    }

    // ---------- Public Repo Stats (no auth needed for public repos) ----------

    @SuppressWarnings("unchecked")
    public PublicRepoStatsDto getPublicRepoStats(UUID userId, UUID repoId) {
        var repo = trackedRepoRepository.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));

        OffsetDateTime epoch = OffsetDateTime.parse("2000-01-01T00:00:00Z");
        OffsetDateTime now = OffsetDateTime.now();

        long totalCommits = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM commits WHERE repo_id = :repoId")
            .setParameter("repoId", repoId).getSingleResult()).longValue();

        Object[] prStats = (Object[]) em.createNativeQuery(
                "SELECT COUNT(*), COUNT(CASE WHEN merged_at IS NOT NULL THEN 1 END), " +
                "AVG(CASE WHEN merged_at IS NOT NULL THEN EXTRACT(EPOCH FROM (merged_at - created_at))/3600 END) " +
                "FROM pull_requests WHERE repo_id = :repoId")
            .setParameter("repoId", repoId).getSingleResult();
        long totalPRs = ((Number) prStats[0]).longValue();
        long mergedPRs = ((Number) prStats[1]).longValue();
        double avgMerge = prStats[2] != null ? ((Number) prStats[2]).doubleValue() : 0;

        // Top contributor
        List<Object[]> topRows = em.createNativeQuery(
                "SELECT author_login, COUNT(*) cnt FROM commits WHERE repo_id = :repoId " +
                "GROUP BY author_login ORDER BY cnt DESC LIMIT 1")
            .setParameter("repoId", repoId).getResultList();
        String topContributor = topRows.isEmpty() ? null : (String) topRows.get(0)[0];
        long topCount = topRows.isEmpty() ? 0 : ((Number) topRows.get(0)[1]).longValue();
        int topPct = totalCommits > 0 ? (int) (topCount * 100 / totalCommits) : 0;

        RepoHealthDto health = getRepoHealth(userId, repoId);

        return new PublicRepoStatsDto(
            repo.getFullName(), null,
            0, 0, 0,  // stars/forks/openIssues — filled from GitHub API on-demand or from language sync
            null, Map.of(),
            health.score(), health.label(),
            totalCommits, totalPRs, mergedPRs, avgMerge,
            topContributor, topPct, true
        );
    }

    // ---------- Commit Trend ----------

    @SuppressWarnings("unchecked")
    public List<CommitTrendDto> getCommitTrend(UUID userId, String login, String granularity,
                                                OffsetDateTime from, OffsetDateTime to) {
        String truncFn = "week".equalsIgnoreCase(granularity) ? "week" : "day";
        String sql = "SELECT DATE_TRUNC('" + truncFn + "', c.committed_at) AS period, COUNT(*) AS cnt " +
                     "FROM commits c JOIN tracked_repos r ON c.repo_id = r.id " +
                     "WHERE r.user_id = :userId AND c.author_login = :login " +
                     "AND c.committed_at BETWEEN :from AND :to " +
                     "GROUP BY period ORDER BY period ASC";
        List<Object[]> rows = em.createNativeQuery(sql)
            .setParameter("userId", userId)
            .setParameter("login", login)
            .setParameter("from", from)
            .setParameter("to", to)
            .getResultList();
        return rows.stream()
            .map(r -> {
                String date;
                if (r[0] instanceof java.sql.Timestamp ts) {
                    date = ts.toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString();
                } else if (r[0] instanceof java.time.Instant inst) {
                    date = inst.atOffset(ZoneOffset.UTC).toLocalDate().toString();
                } else {
                    date = r[0].toString().substring(0, 10);
                }
                return new CommitTrendDto(date, ((Number) r[1]).longValue());
            })
            .toList();
    }

    // ---------- Overview ----------

    public OverviewDto getOverview(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        long commits = countCommits(userId, login, from, to);

        String prSql = "SELECT COUNT(*) FROM pull_requests pr JOIN tracked_repos r ON pr.repo_id = r.id " +
                       "WHERE r.user_id = :userId AND pr.author_login = :login AND pr.created_at BETWEEN :from AND :to";
        long prsAuthored = ((Number) em.createNativeQuery(prSql)
            .setParameter("userId", userId).setParameter("login", login)
            .setParameter("from", from).setParameter("to", to)
            .getSingleResult()).longValue();

        String reviewSql = "SELECT COUNT(*) FROM pr_reviews rv " +
                           "JOIN pull_requests pr ON rv.pr_id = pr.id " +
                           "JOIN tracked_repos r ON pr.repo_id = r.id " +
                           "WHERE r.user_id = :userId AND rv.reviewer_login = :login " +
                           "AND rv.submitted_at BETWEEN :from AND :to";
        long reviewsGiven = ((Number) em.createNativeQuery(reviewSql)
            .setParameter("userId", userId).setParameter("login", login)
            .setParameter("from", from).setParameter("to", to)
            .getSingleResult()).longValue();

        String addSql = "SELECT COALESCE(SUM(c.additions), 0), COALESCE(SUM(c.deletions), 0) " +
                        "FROM commits c JOIN tracked_repos r ON c.repo_id = r.id " +
                        "WHERE r.user_id = :userId AND c.author_login = :login " +
                        "AND c.committed_at BETWEEN :from AND :to";
        Object[] addRow = (Object[]) em.createNativeQuery(addSql)
            .setParameter("userId", userId).setParameter("login", login)
            .setParameter("from", from).setParameter("to", to)
            .getSingleResult();
        long linesAdded = ((Number) addRow[0]).longValue();
        long linesRemoved = ((Number) addRow[1]).longValue();

        return new OverviewDto(commits, prsAuthored, reviewsGiven, linesAdded, linesRemoved);
    }

    // ---------- AI Summary ----------

    public record AiSummaryDto(String summary, boolean aiPowered) {}

    public AiSummaryDto getAiSummary(UUID userId, String login, String timezone,
                                      OffsetDateTime from, OffsetDateTime to) {
        if (!groqApiClient.isConfigured()) {
            return new AiSummaryDto("Connect Groq API to generate AI-powered summaries.", false);
        }

        OffsetDateTime effectiveTo = to != null ? to : OffsetDateTime.now();
        OffsetDateTime effectiveFrom = from != null ? from : effectiveTo.minusDays(30);
        long windowDays = java.time.Duration.between(effectiveFrom, effectiveTo).toDays();

        long commits = countCommits(userId, login, effectiveFrom, effectiveTo);
        StreakDto streak = getStreak(userId, login, timezone);
        PRLifecycleDto lifecycle = getPRLifecycle(userId, effectiveFrom, effectiveTo);

        long stale = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM pull_requests pr JOIN tracked_repos r ON pr.repo_id = r.id " +
                "WHERE r.user_id = :userId AND pr.state = 'OPEN' AND pr.created_at < NOW() - INTERVAL '7 days'")
            .setParameter("userId", userId).getSingleResult()).longValue();

        String prompt = String.format(
            "Developer '%s' stats (last %d days): %d commits, %d PRs merged, avg merge time %.0f hours, " +
            "%d day streak, %d stale PRs open. " +
            "Write a 2-3 sentence coaching summary: highlight what's going well, flag one concern if any, give one actionable tip. " +
            "Be specific, concise, and encouraging. No bullet points. Plain text only.",
            login, windowDays, commits, lifecycle.getMergedCount(), lifecycle.getAvgHoursToMerge(),
            streak.getCurrentStreak(), stale
        );

        String summary = groqApiClient.complete(
            "You are a senior engineering coach analyzing developer productivity metrics. Be direct and human.",
            prompt
        );

        return summary != null
            ? new AiSummaryDto(summary, true)
            : new AiSummaryDto("Could not generate summary at this time.", false);
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
