package com.gitanalytics.analytics.service;

import com.gitanalytics.analytics.dao.AnalyticsDao;
import com.gitanalytics.analytics.dto.*;
import com.gitanalytics.auth.dao.UserDao;
import com.gitanalytics.auth.entity.User;
import com.gitanalytics.ingestion.dao.CommitDao;
import com.gitanalytics.ingestion.dao.PullRequestDao;
import com.gitanalytics.ingestion.dao.ReleaseDao;
import com.gitanalytics.ingestion.entity.Commit;
import com.gitanalytics.ingestion.dao.TrackedRepoDao;
import com.gitanalytics.ingestion.entity.PullRequest;
import com.gitanalytics.shared.client.GroqApiClient;
import com.gitanalytics.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsDao analyticsDao;
    private final UserDao userDao;
    private final CommitDao commitDao;
    private final PullRequestDao pullRequestDao;
    private final ReleaseDao releaseDao;
    private final TrackedRepoDao trackedRepoDao;
    private final RedisTemplate<String, Object> redisTemplate;
    private final GroqApiClient groqApiClient;

    // ── Timezone Helper ───────────────────────────────────────────────────────

    private ZoneId parseTimezone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timezone: " + timezone);
        }
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    public DashboardSummaryDto getDashboard(UUID userId) {
        String cacheKey = "ga:dashboard:" + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof DashboardSummaryDto dto) return dto;

        User user = userDao.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        OffsetDateTime weekAgo = OffsetDateTime.now().minusWeeks(1);
        OffsetDateTime monthAgo = OffsetDateTime.now().minusMonths(1);
        OffsetDateTime now = OffsetDateTime.now();

        long weeklyCommits = analyticsDao.countCommits(userId, user.getUsername(), weekAgo, now);

        List<PullRequest> recentPrs = pullRequestDao.findByUserAndAuthorAndDateRange(
            userId, user.getUsername(), monthAgo, now);

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
                pr.getState().name(), pr.getCreatedAt())).toList())
            .build();

        redisTemplate.opsForValue().set(cacheKey, dto, 5, TimeUnit.MINUTES);
        return dto;
    }

    // ── Commit Heatmap ────────────────────────────────────────────────────────

    public List<HeatmapCellDto> getCommitHeatmap(UUID userId, String repoId, String timezone,
                                                   OffsetDateTime from, OffsetDateTime to) {
        String tz = timezone != null ? timezone : "UTC";
        parseTimezone(tz); // validate early
        UUID repoUuid = null;
        if (repoId != null) {
            try {
                repoUuid = UUID.fromString(repoId);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid repoId: " + repoId);
            }
        }
        return analyticsDao.getCommitHeatmap(
            userId,
            tz,
            repoUuid,
            from, to);
    }

    // ── PR Lifecycle ──────────────────────────────────────────────────────────

    public PRLifecycleDto getPRLifecycle(UUID userId, OffsetDateTime from, OffsetDateTime to) {
        Object[] row = analyticsDao.getPRLifecycleStats(userId, from, to);
        return PRLifecycleDto.builder()
            .avgHoursToFirstReview(row[0] != null ? ((Number) row[0]).doubleValue() : 0)
            .avgHoursToMerge(row[1] != null ? ((Number) row[1]).doubleValue() : 0)
            .mergedCount(row[2] != null ? ((Number) row[2]).longValue() : 0)
            .totalCount(row[3] != null ? ((Number) row[3]).longValue() : 0)
            .build();
    }

    // ── PR Size Distribution ──────────────────────────────────────────────────

    public PRSizeDistributionDto getPRSizeDistribution(UUID userId, OffsetDateTime from, OffsetDateTime to) {
        User user = userDao.findById(userId).orElseThrow();
        List<PullRequest> prs = pullRequestDao.findByUserAndAuthorAndDateRange(userId, user.getUsername(), from, to);

        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("XS", 0L);
        buckets.put("S", 0L);
        buckets.put("M", 0L);
        buckets.put("L", 0L);
        buckets.put("XL", 0L);

        for (PullRequest pr : prs) {
            int files = pr.getChangedFiles();
            if (files < 10) buckets.merge("XS", 1L, Long::sum);
            else if (files < 50) buckets.merge("S", 1L, Long::sum);
            else if (files < 200) buckets.merge("M", 1L, Long::sum);
            else if (files < 1000) buckets.merge("L", 1L, Long::sum);
            else buckets.merge("XL", 1L, Long::sum);
        }
        return new PRSizeDistributionDto(buckets);
    }

    // ── Team Leaderboard ──────────────────────────────────────────────────────

    public List<ContributorStatsDto> getTeamLeaderboard(UUID userId, UUID repoId,
                                                          OffsetDateTime from, OffsetDateTime to) {
        return analyticsDao.getTeamLeaderboard(userId, repoId, from, to);
    }

    // ── Bus Factor ────────────────────────────────────────────────────────────

    public BusFactorDto getBusFactor(UUID userId, UUID repoId) {
        List<Object[]> rows = analyticsDao.getBusFactorRows(userId, repoId);
        if (rows.isEmpty()) return new BusFactorDto(null, 0, 0);

        long total = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        long top = ((Number) rows.get(0)[1]).longValue();
        double pct = total > 0 ? (double) top / total * 100 : 0;
        return new BusFactorDto((String) rows.get(0)[0], pct, rows.size());
    }

    // ── Contribution Streak ───────────────────────────────────────────────────

    public StreakDto getStreak(UUID userId, String login, String timezone) {
        String cacheKey = "ga:streak:" + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof StreakDto dto) return dto;

        try {
            Object[] row = analyticsDao.getStreakData(userId, login, timezone);
            int longest = row[0] != null ? ((Number) row[0]).intValue() : 0;
            int current = 0;
            if (row.length > 2 && row[1] != null && row[2] != null) {
                LocalDate lastDay = switch (row[2]) {
                    case java.sql.Date d -> d.toLocalDate();
                    case LocalDate d -> d;
                    default -> LocalDate.parse(row[2].toString().substring(0, 10));
                };
                LocalDate today = LocalDate.now(parseTimezone(timezone));
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

    // ── Insights ─────────────────────────────────────────────────────────────

    public List<InsightDto> getInsights(UUID userId, String login, String timezone,
                                        OffsetDateTime from, OffsetDateTime to) {
        List<InsightDto> insights = new ArrayList<>();
        String tz = timezone != null ? timezone : "UTC";
        parseTimezone(tz); // validate early

        OffsetDateTime effectiveTo = to != null ? to : OffsetDateTime.now();
        OffsetDateTime effectiveFrom = from != null ? from : effectiveTo.minusDays(30);
        long windowDays = java.time.Duration.between(effectiveFrom, effectiveTo).toDays();
        String periodLabel = windowDays <= 7 ? "Last 7 Days" : windowDays <= 31 ? "Last 30 Days" :
                             windowDays <= 93 ? "Last 90 Days" : "Selected Period";

        // Peak coding time
        try {
            Object[] peak = analyticsDao.getPeakCodingTime(userId, login, tz);
            String[] dayNames = {"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
            String[] fullDayNames = {"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"};
            int day = ((Number) peak[0]).intValue();
            int hour = ((Number) peak[1]).intValue();
            String ampm = hour < 12 ? (hour == 0 ? "12am" : hour + "am") : (hour == 12 ? "12pm" : (hour - 12) + "pm");
            insights.add(new InsightDto(
                "You're most productive on " + fullDayNames[day] + "s around " + String.format("%02d:00", hour) + ".",
                "info", dayNames[day] + " " + ampm, "Peak Coding Time"));
        } catch (Exception ignored) {}

        // Weekday vs weekend
        try {
            Object[] ww = analyticsDao.getWeekdayWeekendSplit(userId, login, tz);
            long weekday = ((Number) ww[0]).longValue();
            long weekend = ((Number) ww[1]).longValue();
            if (weekday + weekend > 0) {
                int weekendPct = (int) Math.round((double) weekend / (weekday + weekend) * 100);
                int weekdayPct = 100 - weekendPct;
                if (weekendPct >= 30) {
                    insights.add(new InsightDto(
                        "You often code on weekends — " + weekendPct + "% of your commits happen on Sat/Sun.",
                        "info", weekendPct + "%", "Weekend Commits"));
                } else {
                    insights.add(new InsightDto(
                        "You're a weekday coder — " + weekdayPct + "% of your commits happen Mon–Fri.",
                        "info", weekdayPct + "%", "Weekday Commits"));
                }
            }
        } catch (Exception ignored) {}

        // Commit trend comparison
        try {
            OffsetDateTime prevFrom = effectiveFrom.minusDays(windowDays);
            Object[] trend = analyticsDao.getCommitTrendComparison(
                userId, login, effectiveFrom, effectiveTo, prevFrom, effectiveFrom);
            long current = ((Number) trend[0]).longValue();
            long previous = ((Number) trend[1]).longValue();
            if (previous > 0) {
                long changePct = Math.round((double)(current - previous) / previous * 100);
                if (changePct >= 20) {
                    insights.add(new InsightDto("Your commit activity is up " + changePct + "% vs the previous " + windowDays + " days.",
                        "positive", "+" + changePct + "%", "vs Prev Period"));
                } else if (changePct <= -20) {
                    insights.add(new InsightDto("Your commit activity dropped " + Math.abs(changePct) + "% vs the previous " + windowDays + " days.",
                        "warning", changePct + "%", "vs Prev Period"));
                } else {
                    insights.add(new InsightDto("Your commit pace is steady — " + current + " commits this period vs " + previous + " previously.",
                        "info", current + " commits", periodLabel));
                }
            } else if (current > 0) {
                insights.add(new InsightDto("You've made " + current + " commits in this period — great momentum!",
                    "positive", String.valueOf(current), periodLabel));
            }
        } catch (Exception ignored) {}

        // Stale PRs
        try {
            long stale = analyticsDao.countOpenStalePRsForUser(userId);
            if (stale > 0) {
                insights.add(new InsightDto(
                    stale + " PR" + (stale > 1 ? "s have" : " has") + " been open for over 7 days without being merged.",
                    "warning", String.valueOf(stale), "Stale PRs"));
            }
        } catch (Exception ignored) {}

        // PR merge time
        try {
            Double avgHours = analyticsDao.getAvgMergeTimeForUser(userId, login, effectiveFrom, effectiveTo);
            if (avgHours != null) {
                String metricVal = avgHours < 1 ? Math.round(avgHours * 60) + "m" : String.format("%.0fh", avgHours);
                if (avgHours < 4) {
                    insights.add(new InsightDto("Your PRs are merged in under 4 hours on average — excellent turnaround!", "positive", metricVal, "Avg Merge Time"));
                } else if (avgHours > 48) {
                    insights.add(new InsightDto("Consider breaking PRs into smaller chunks for faster reviews.", "warning", metricVal, "Avg Merge Time"));
                } else {
                    insights.add(new InsightDto("Your average PR merge time is " + metricVal + ".", "info", metricVal, "Avg Merge Time"));
                }
            }
        } catch (Exception ignored) {}

        // Review style
        try {
            Object[] rv = analyticsDao.getReviewStyleStats(userId, login, effectiveFrom, effectiveTo);
            long approved = ((Number) rv[0]).longValue();
            long changesRequested = ((Number) rv[1]).longValue();
            long total = ((Number) rv[2]).longValue();
            if (total >= 5) {
                int approvePct = (int) Math.round((double) approved / total * 100);
                if (approvePct >= 80) {
                    insights.add(new InsightDto("You approve " + approvePct + "% of PRs you review — you're a collaborative reviewer.",
                        "positive", approvePct + "%", "Approval Rate"));
                } else if (changesRequested > approved) {
                    insights.add(new InsightDto("You request changes more often than you approve — you hold the bar high.",
                        "info", approvePct + "%", "Approval Rate"));
                }
            }
        } catch (Exception ignored) {}

        if (insights.isEmpty()) {
            try {
                long total = analyticsDao.countTotalCommitsForUser(userId, login);
                if (total > 0) {
                    insights.add(new InsightDto("You have " + total + " commits tracked across your repositories.", "info", String.valueOf(total), "Total Commits"));
                } else {
                    insights.add(new InsightDto("Sync a repository to start seeing personalized insights.", "info"));
                }
            } catch (Exception ignored) {
                insights.add(new InsightDto("Sync a repository to start seeing personalized insights.", "info"));
            }
        }

        if (!insights.isEmpty()) {
            try {
                String summary = insights.stream().map(InsightDto::message)
                    .collect(java.util.stream.Collectors.joining("; "));
                String aiText = groqApiClient.complete(
                    "You are a developer productivity coach. Given metrics about a developer, write one encouraging, specific insight in 1-2 sentences. Be direct and concrete. No fluff.",
                    "Developer stats: " + summary + ". Give one actionable insight.");
                if (aiText != null && !aiText.isBlank()) {
                    insights.add(new InsightDto(aiText, "info", null, "AI Insight"));
                }
            } catch (Exception ignored) {}
        }

        return insights;
    }

    // ── Reviews Summary ───────────────────────────────────────────────────────

    public ReviewsSummaryDto getReviewsSummary(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        Object[] row = analyticsDao.getReviewsSummaryRow(userId, login, from, to);
        long total = row[0] != null ? ((Number) row[0]).longValue() : 0;
        long approved = row[1] != null ? ((Number) row[1]).longValue() : 0;
        long changesRequested = row[2] != null ? ((Number) row[2]).longValue() : 0;
        long commented = row[3] != null ? ((Number) row[3]).longValue() : 0;
        long prsReviewed = row[4] != null ? ((Number) row[4]).longValue() : 0;
        return new ReviewsSummaryDto(total, approved, changesRequested, commented,
            prsReviewed > 0 ? (double) total / prsReviewed : 0);
    }

    // ── Stale PRs ─────────────────────────────────────────────────────────────

    public List<PrSummaryDto> getStalePRs(UUID userId, UUID repoId, int olderThanDays) {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(olderThanDays);
        return pullRequestDao.findStalePRs(userId, repoId, threshold).stream()
            .map(pr -> new PrSummaryDto(pr.getId(), pr.getPrNumber(), pr.getTitle(),
                pr.getState().name(), pr.getCreatedAt()))
            .toList();
    }

    // ── Repo Health ───────────────────────────────────────────────────────────

    public RepoHealthDto getRepoHealth(UUID userId, UUID repoId) {
        trackedRepoDao.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime thirtyDaysAgo = now.minusDays(30);
        OffsetDateTime fourteenDaysAgo = now.minusDays(14);

        List<RepoHealthDto.SignalDto> signals = new ArrayList<>();
        int passed = 0;

        long recentCommits = analyticsDao.countRecentCommits(repoId, thirtyDaysAgo);
        boolean active = recentCommits >= 5;
        if (active) passed++;
        signals.add(new RepoHealthDto.SignalDto("Commit velocity (30d)", active,
            recentCommits == 0 ? "No commits in 30 days" :
            active ? recentCommits + " commits" : recentCommits + " commits — needs 5+"));

        Object[] reviewCoverage = analyticsDao.getPRReviewCoverage(repoId, thirtyDaysAgo);
        long totalPRs30d = ((Number) reviewCoverage[0]).longValue();
        long reviewedPRs = ((Number) reviewCoverage[1]).longValue();
        boolean goodCoverage = totalPRs30d > 0 && (reviewedPRs * 100 / totalPRs30d) >= 50;
        if (goodCoverage) passed++;
        signals.add(new RepoHealthDto.SignalDto("PR review coverage", goodCoverage,
            totalPRs30d == 0 ? "No PRs opened in 30 days" : reviewedPRs + "/" + totalPRs30d + " PRs reviewed"));

        if (recentCommits > 0) {
            List<Object[]> topAuthors = analyticsDao.getTopAuthorRows(repoId, thirtyDaysAgo);
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

        Double avgMerge = analyticsDao.getAvgMergeTimeHours(repoId, thirtyDaysAgo);
        boolean fastMerge = avgMerge == null || avgMerge < 48;
        if (fastMerge) passed++;
        signals.add(new RepoHealthDto.SignalDto("PR merge time", fastMerge,
            avgMerge == null ? "No merged PRs" : String.format("%.1fh avg", avgMerge)));

        long stalePRs = analyticsDao.countStalePRsByRepo(repoId, fourteenDaysAgo);
        boolean noStale = stalePRs == 0;
        if (noStale) passed++;
        signals.add(new RepoHealthDto.SignalDto("No stale PRs (>14d)", noStale,
            noStale ? "All clear" : stalePRs + " stale PR" + (stalePRs != 1 ? "s" : "")));

        Optional<OffsetDateTime> latestRelease = releaseDao.findLatestPublishedAt(repoId);
        boolean hasRecentRelease = latestRelease.map(d -> d.isAfter(OffsetDateTime.now().minusDays(90))).orElse(false);
        if (hasRecentRelease) passed++;
        signals.add(new RepoHealthDto.SignalDto("Recent release (90d)", hasRecentRelease,
            latestRelease.map(d -> "Last release: " + d.toLocalDate()).orElse("No releases tracked")));

        int score = (int) Math.round((double) passed / signals.size() * 100);
        String label = score >= 80 ? "Healthy" : score >= 60 ? "At Risk" : "Needs Attention";
        return new RepoHealthDto(score, label, signals);
    }

    // ── Public Repo Stats ─────────────────────────────────────────────────────

    public PublicRepoStatsDto getPublicRepoStats(UUID userId, UUID repoId) {
        var repo = trackedRepoDao.findById(repoId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));

        long totalCommits = analyticsDao.countCommitsByRepo(repoId);

        Object[] prStats = analyticsDao.getPRStatsByRepo(repoId);
        long totalPRs = ((Number) prStats[0]).longValue();
        long mergedPRs = ((Number) prStats[1]).longValue();
        double avgMerge = prStats[2] != null ? ((Number) prStats[2]).doubleValue() : 0;

        List<Object[]> topRows = analyticsDao.getTopContributorByRepo(repoId);
        String topContributor = topRows.isEmpty() ? null : (String) topRows.get(0)[0];
        long topCount = topRows.isEmpty() ? 0 : ((Number) topRows.get(0)[1]).longValue();
        int topPct = totalCommits > 0 ? (int) (topCount * 100 / totalCommits) : 0;

        RepoHealthDto health = getRepoHealth(userId, repoId);

        return new PublicRepoStatsDto(
            repo.getFullName(), null, 0, 0, 0, null, Map.of(),
            health.score(), health.label(),
            totalCommits, totalPRs, mergedPRs, avgMerge,
            topContributor, topPct, true);
    }

    // ── Commit Trend ──────────────────────────────────────────────────────────

    public List<CommitTrendDto> getCommitTrend(UUID userId, String login, String granularity,
                                                OffsetDateTime from, OffsetDateTime to) {
        return analyticsDao.getCommitTrend(userId, login, granularity, from, to);
    }

    // ── Overview ──────────────────────────────────────────────────────────────

    public OverviewDto getOverview(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        long commits = analyticsDao.countCommits(userId, login, from, to);
        long prsAuthored = analyticsDao.countPRsAuthored(userId, login, from, to);
        long reviewsGiven = analyticsDao.countReviewsGiven(userId, login, from, to);
        Object[] lines = analyticsDao.getLinesAddedRemoved(userId, login, from, to);
        long linesAdded = ((Number) lines[0]).longValue();
        long linesRemoved = ((Number) lines[1]).longValue();
        return new OverviewDto(commits, prsAuthored, reviewsGiven, linesAdded, linesRemoved);
    }

    // ── AI Summary ────────────────────────────────────────────────────────────

    public record AiSummaryDto(String summary, boolean aiPowered) {}

    public AiSummaryDto getAiSummary(UUID userId, String login, String timezone,
                                      OffsetDateTime from, OffsetDateTime to) {
        if (!groqApiClient.isConfigured()) {
            return new AiSummaryDto("Connect Groq API to generate AI-powered summaries.", false);
        }

        OffsetDateTime effectiveTo = to != null ? to : OffsetDateTime.now();
        OffsetDateTime effectiveFrom = from != null ? from : effectiveTo.minusDays(30);
        long windowDays = java.time.Duration.between(effectiveFrom, effectiveTo).toDays();

        long commits = analyticsDao.countCommits(userId, login, effectiveFrom, effectiveTo);
        StreakDto streak = getStreak(userId, login, timezone);
        PRLifecycleDto lifecycle = getPRLifecycle(userId, effectiveFrom, effectiveTo);
        long stale = analyticsDao.countOpenStalePRsOlderThan(userId, "7");

        String prompt = String.format(
            "Developer '%s' stats (last %d days): %d commits, %d PRs merged, avg merge time %.0f hours, " +
            "%d day streak, %d stale PRs open. " +
            "Write a 2-3 sentence coaching summary: highlight what's going well, flag one concern if any, give one actionable tip. " +
            "Be specific, concise, and encouraging. No bullet points. Plain text only.",
            login, windowDays, commits, lifecycle.getMergedCount(), lifecycle.getAvgHoursToMerge(),
            streak.getCurrentStreak(), stale);

        String summary = groqApiClient.complete(
            "You are a senior engineering coach analyzing developer productivity metrics. Be direct and human.",
            prompt);

        return summary != null
            ? new AiSummaryDto(summary, true)
            : new AiSummaryDto("Could not generate summary at this time.", false);
    }

    // ── Activity Feed ─────────────────────────────────────────────────────────

    public List<ActivityEvent> getActivityFeed(UUID userId, int limit) {
        User user = userDao.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String login = user.getUsername();

        int half = Math.max(1, limit / 2);

        List<Commit> commits = commitDao.findRecentByUser(userId, login, half);
        List<PullRequest> prs = pullRequestDao.findRecentByUser(userId, login, half);

        List<ActivityEvent> events = new ArrayList<>();

        for (Commit c : commits) {
            events.add(ActivityEvent.builder()
                .type("COMMIT")
                .title(c.getMessageSummary())
                .repoFullName(c.getRepo().getFullName())
                .sha(c.getSha())
                .prNumber(null)
                .state(null)
                .linesAdded(c.getAdditions())
                .linesRemoved(c.getDeletions())
                .occurredAt(c.getCommittedAt().toString())
                .build());
        }

        for (PullRequest pr : prs) {
            String type = switch (pr.getState()) {
                case MERGED -> "PR_MERGED";
                case CLOSED -> "PR_CLOSED";
                default -> "PR_OPENED";
            };
            events.add(ActivityEvent.builder()
                .type(type)
                .title(pr.getTitle())
                .repoFullName(pr.getRepo().getFullName())
                .sha(null)
                .prNumber(pr.getPrNumber())
                .state(pr.getState().name())
                .linesAdded(null)
                .linesRemoved(null)
                .occurredAt(pr.getCreatedAt().toString())
                .build());
        }

        events.sort(Comparator.comparing(ActivityEvent::getOccurredAt).reversed());
        return events.stream().limit(limit).toList();
    }

    // ── Churn Leaderboard ─────────────────────────────────────────────────────

    public List<ContributorStatsDto> getChurnLeaderboard(UUID userId, UUID repoId,
                                                          OffsetDateTime from, OffsetDateTime to) {
        return analyticsDao.getChurnLeaderboard(userId, repoId, from, to);
    }

    // ── PR Merge Rate Trend ───────────────────────────────────────────────────

    public List<PRMergeRateDto> getPRMergeRateTrend(UUID userId, String login,
                                                     OffsetDateTime from, OffsetDateTime to) {
        return analyticsDao.getPRMergeRateTrend(userId, login, from, to);
    }

    // ── Reviewer Coverage ─────────────────────────────────────────────────────

    public ReviewerCoverageDto getReviewerCoverage(UUID userId, String login,
                                                    OffsetDateTime from, OffsetDateTime to) {
        Object[] row = analyticsDao.getReviewerCoverageForUser(userId, login, from, to);
        long totalPRs = row[0] != null ? ((Number) row[0]).longValue() : 0;
        long reviewedPRs = row[1] != null ? ((Number) row[1]).longValue() : 0;
        double pct = totalPRs > 0 ? (double) reviewedPRs / totalPRs * 100 : 0;
        return new ReviewerCoverageDto(totalPRs, reviewedPRs, pct);
    }

    // ── Collaboration ─────────────────────────────────────────────────────────

    public CollaborationDto getCollaboration(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        List<CollaborationDto.CollaboratorEntry> reviewersOfMe = analyticsDao
            .getTopReviewersOfMyPRs(userId, login, from, to).stream()
            .map(r -> new CollaborationDto.CollaboratorEntry((String) r[0], ((Number) r[1]).longValue()))
            .toList();

        List<CollaborationDto.CollaboratorEntry> iReviewFor = analyticsDao
            .getTopPeopleIReview(userId, login, from, to).stream()
            .map(r -> new CollaborationDto.CollaboratorEntry((String) r[0], ((Number) r[1]).longValue()))
            .toList();

        return new CollaborationDto(reviewersOfMe, iReviewFor);
    }

    // ── Repo Commit Trend ─────────────────────────────────────────────────────

    public List<CommitTrendDto> getCommitTrendByRepo(UUID userId, UUID repoId, OffsetDateTime from, OffsetDateTime to) {
        trackedRepoDao.findById(repoId)
            .filter(r -> r.getUser().getId().equals(userId))
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
        return analyticsDao.getCommitTrendByRepo(repoId, from, to);
    }

    // ── Stars & Forks Trend ───────────────────────────────────────────────────

    public List<StarsForksSnapshotDto> getStarsForksTrend(UUID userId, UUID repoId) {
        trackedRepoDao.findById(repoId)
            .filter(r -> r.getUser().getId().equals(userId))
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
        return analyticsDao.getStarsForksTrend(repoId);
    }

    // ── Release Trend ─────────────────────────────────────────────────────────

    public List<ReleaseTrendDto> getReleaseTrend(UUID userId, UUID repoId) {
        trackedRepoDao.findById(repoId)
            .filter(r -> r.getUser().getId().equals(userId))
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
        return analyticsDao.getReleaseTrend(repoId);
    }

    // ── Issue Analytics ───────────────────────────────────────────────────────

    public IssueAnalyticsDto getIssueAnalytics(UUID userId, UUID repoId) {
        trackedRepoDao.findById(repoId)
            .filter(r -> r.getUser().getId().equals(userId))
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
        return analyticsDao.getIssueAnalytics(repoId);
    }

    // ── Language Bytes ────────────────────────────────────────────────────────

    public List<RepoLanguageDto> getLanguageBytes(UUID userId, UUID repoId) {
        trackedRepoDao.findById(repoId)
            .filter(r -> r.getUser().getId().equals(userId))
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
        return analyticsDao.getLanguageBytes(repoId);
    }

    // ── Compare Two Repos ─────────────────────────────────────────────────────

    public List<RepoCompareDto> compareRepos(UUID userId, List<UUID> repoIds) {
        return repoIds.stream().map(repoId -> {
            var repo = trackedRepoDao.findById(repoId)
                .filter(r -> r.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found: " + repoId));

            RepoHealthDto health = getRepoHealth(userId, repoId);
            long totalCommits = analyticsDao.countCommitsByRepo(repoId);

            Object[] prStats = analyticsDao.getPRStatsByRepo(repoId);
            long totalPRs  = prStats[0] != null ? ((Number) prStats[0]).longValue() : 0;
            long mergedPRs = prStats[1] != null ? ((Number) prStats[1]).longValue() : 0;
            double avgMerge = prStats[2] != null ? ((Number) prStats[2]).doubleValue() : 0;

            List<Object[]> topRows = analyticsDao.getTopContributorByRepo(repoId);
            String topContributor = topRows.isEmpty() ? null : (String) topRows.get(0)[0];
            long topCount = topRows.isEmpty() ? 0 : ((Number) topRows.get(0)[1]).longValue();
            int topPct = totalCommits > 0 ? (int) (topCount * 100 / totalCommits) : 0;

            return new RepoCompareDto(
                repoId.toString(), repo.getFullName(),
                health.score(), health.label(),
                totalCommits, totalPRs, mergedPRs, avgMerge,
                topContributor, topPct,
                repo.getStars() != null ? repo.getStars() : 0,
                repo.getForks() != null ? repo.getForks() : 0,
                repo.getOpenIssuesCount() != null ? repo.getOpenIssuesCount() : 0
            );
        }).toList();
    }

    // ── Compare Two Contributors ──────────────────────────────────────────────

    public List<ContributorCompareDto> compareContributors(UUID userId, List<String> logins) {
        return logins.stream()
            .map(login -> analyticsDao.getContributorStats(userId, login))
            .toList();
    }

    // ── Review Queue ───────────────────────────────────────────────────────────

    public List<ReviewQueueItem> getReviewQueue(UUID userId) {
        User user = userDao.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String login = user.getUsername();

        List<PullRequest> prs = pullRequestDao.findReviewQueue(userId, login);

        return prs.stream().map(pr -> {
            int total = pr.getAdditions() + pr.getDeletions();
            String sizeLabel;
            if (total < 10)       sizeLabel = "XS";
            else if (total < 50)  sizeLabel = "S";
            else if (total < 200) sizeLabel = "M";
            else if (total < 500) sizeLabel = "L";
            else                  sizeLabel = "XL";

            long ageHours = java.time.Duration.between(pr.getCreatedAt(), OffsetDateTime.now()).toHours();

            return ReviewQueueItem.builder()
                .id(pr.getId())
                .prNumber(pr.getPrNumber())
                .title(pr.getTitle())
                .repoFullName(pr.getRepo().getFullName())
                .authorLogin(pr.getAuthorLogin())
                .createdAt(pr.getCreatedAt().toString())
                .additions(pr.getAdditions())
                .deletions(pr.getDeletions())
                .changedFiles(pr.getChangedFiles())
                .ageHours(ageHours)
                .sizeLabel(sizeLabel)
                .build();
        }).toList();
    }
}
