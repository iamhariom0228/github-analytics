package com.gitanalytics.analytics.dao.impl;

import com.gitanalytics.analytics.dao.AnalyticsDao;
import com.gitanalytics.analytics.dto.CommitTrendDto;
import com.gitanalytics.analytics.dto.ContributorStatsDto;
import com.gitanalytics.analytics.dto.HeatmapCellDto;
import com.gitanalytics.analytics.repository.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AnalyticsDaoImpl implements AnalyticsDao {

    private final AnalyticsRepository analyticsRepository;

    // ── Heatmap ──────────────────────────────────────────────────────────────

    @Override
    public List<HeatmapCellDto> getCommitHeatmap(UUID userId, String timezone, UUID repoId,
                                                  OffsetDateTime from, OffsetDateTime to) {
        String tz = timezone != null ? timezone : "UTC";
        List<Object[]> rows = repoId != null
                ? analyticsRepository.getCommitHeatmapByRepo(userId, tz, repoId, from, to)
                : analyticsRepository.getCommitHeatmap(userId, tz, from, to);
        return rows.stream()
                .map(r -> new HeatmapCellDto(
                        ((Number) r[0]).intValue(),
                        ((Number) r[1]).intValue(),
                        ((Number) r[2]).intValue()))
                .toList();
    }

    // Spring Data JPA wraps single-row native query results as Object[]{row},
    // where each element is itself the Object[] row. Unwrap one level.
    private static Object[] firstRow(Object[] result) {
        if (result == null || result.length == 0) return result;
        if (result[0] instanceof Object[]) return (Object[]) result[0];
        return result;
    }

    // ── PR Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public Object[] getPRLifecycleStats(UUID userId, OffsetDateTime from, OffsetDateTime to) {
        return firstRow(analyticsRepository.getPRLifecycleStats(userId, from, to));
    }

    // ── Team Leaderboard ─────────────────────────────────────────────────────

    @Override
    public List<ContributorStatsDto> getTeamLeaderboard(UUID userId, UUID repoId,
                                                        OffsetDateTime from, OffsetDateTime to) {
        return analyticsRepository.getTeamLeaderboard(userId, repoId, from, to).stream()
                .map(r -> new ContributorStatsDto(
                        (String) r[0],
                        ((Number) r[1]).longValue(),
                        r[2] != null ? ((Number) r[2]).longValue() : 0,
                        r[3] != null ? ((Number) r[3]).longValue() : 0))
                .toList();
    }

    // ── Bus Factor ───────────────────────────────────────────────────────────

    @Override
    public List<Object[]> getBusFactorRows(UUID userId, UUID repoId) {
        return analyticsRepository.getBusFactorRows(userId, repoId);
    }

    // ── Reviews Summary ──────────────────────────────────────────────────────

    @Override
    public Object[] getReviewsSummaryRow(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        return firstRow(analyticsRepository.getReviewsSummaryRow(userId, login, from, to));
    }

    // ── Repo Health Signals ───────────────────────────────────────────────────

    @Override
    public long countRecentCommits(UUID repoId, OffsetDateTime since) {
        return analyticsRepository.countRecentCommits(repoId, since);
    }

    @Override
    public Object[] getPRReviewCoverage(UUID repoId, OffsetDateTime since) {
        return firstRow(analyticsRepository.getPRReviewCoverage(repoId, since));
    }

    @Override
    public List<Object[]> getTopAuthorRows(UUID repoId, OffsetDateTime since) {
        return analyticsRepository.getTopAuthorRows(repoId, since);
    }

    @Override
    public Double getAvgMergeTimeHours(UUID repoId, OffsetDateTime since) {
        return analyticsRepository.getAvgMergeTimeHours(repoId, since);
    }

    @Override
    public long countStalePRsByRepo(UUID repoId, OffsetDateTime before) {
        return analyticsRepository.countStalePRsByRepo(repoId, before);
    }

    // ── Commit Trend ─────────────────────────────────────────────────────────

    @Override
    public List<CommitTrendDto> getCommitTrend(UUID userId, String login, String granularity,
                                               OffsetDateTime from, OffsetDateTime to) {
        String truncUnit = "weekly".equalsIgnoreCase(granularity) ? "week" : "day";
        return analyticsRepository.getCommitTrend(userId, login, truncUnit, from, to).stream()
                .map(r -> {
                    String date;
                    if (r[0] instanceof java.sql.Timestamp ts)
                        date = ts.toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString();
                    else if (r[0] instanceof java.time.Instant inst)
                        date = inst.atOffset(ZoneOffset.UTC).toLocalDate().toString();
                    else
                        date = r[0].toString().substring(0, 10);
                    return new CommitTrendDto(date, ((Number) r[1]).longValue());
                })
                .toList();
    }

    // ── Overview ─────────────────────────────────────────────────────────────

    @Override
    public long countCommits(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        return analyticsRepository.countCommits(userId, login, from, to);
    }

    @Override
    public long countPRsAuthored(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        return analyticsRepository.countPRsAuthored(userId, login, from, to);
    }

    @Override
    public long countReviewsGiven(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        return analyticsRepository.countReviewsGiven(userId, login, from, to);
    }

    @Override
    public Object[] getLinesAddedRemoved(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        return firstRow(analyticsRepository.getLinesAddedRemoved(userId, login, from, to));
    }

    // ── Insights ─────────────────────────────────────────────────────────────

    @Override
    public Object[] getPeakCodingTime(UUID userId, String login, String timezone) {
        return analyticsRepository.getPeakCodingTime(userId, login, timezone)
                .map(AnalyticsDaoImpl::firstRow)
                .orElse(null);
    }

    @Override
    public Object[] getWeekdayWeekendSplit(UUID userId, String login, String timezone) {
        return firstRow(analyticsRepository.getWeekdayWeekendSplit(userId, login, timezone));
    }

    @Override
    public Object[] getCommitTrendComparison(UUID userId, String login,
                                             OffsetDateTime from, OffsetDateTime to,
                                             OffsetDateTime prevFrom, OffsetDateTime prevTo) {
        return firstRow(analyticsRepository.getCommitTrendComparison(userId, login, from, to, prevFrom, prevTo));
    }

    @Override
    public long countOpenStalePRsForUser(UUID userId) {
        return analyticsRepository.countOpenStalePRs(userId, OffsetDateTime.now().minusDays(7));
    }

    @Override
    public Double getAvgMergeTimeForUser(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        return analyticsRepository.getAvgMergeTimeForUser(userId, login, from, to);
    }

    @Override
    public Object[] getReviewStyleStats(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        return firstRow(analyticsRepository.getReviewStyleStats(userId, login, from, to));
    }

    @Override
    public long countTotalCommitsForUser(UUID userId, String login) {
        return analyticsRepository.countTotalCommitsForUser(userId, login);
    }

    // ── Streak ───────────────────────────────────────────────────────────────

    @Override
    public Object[] getStreakData(UUID userId, String login, String timezone) {
        return firstRow(analyticsRepository.getStreakData(userId, login, timezone));
    }

    // ── Public Repo Stats ────────────────────────────────────────────────────

    @Override
    public long countCommitsByRepo(UUID repoId) {
        return analyticsRepository.countCommitsByRepo(repoId);
    }

    @Override
    public Object[] getPRStatsByRepo(UUID repoId) {
        return firstRow(analyticsRepository.getPRStatsByRepo(repoId));
    }

    @Override
    public List<Object[]> getTopContributorByRepo(UUID repoId) {
        return analyticsRepository.getTopContributorByRepo(repoId);
    }

    // ── AI Summary Helper ────────────────────────────────────────────────────

    @Override
    public long countOpenStalePRsOlderThan(UUID userId, String intervalDays) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(Long.parseLong(intervalDays));
        return analyticsRepository.countOpenStalePRs(userId, cutoff);
    }

    // ── Collaboration ─────────────────────────────────────────────────────────

    @Override
    public List<Object[]> getTopReviewersOfMyPRs(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        return analyticsRepository.getTopReviewersOfMyPRs(userId, login, from, to);
    }

    @Override
    public List<Object[]> getTopPeopleIReview(UUID userId, String login, OffsetDateTime from, OffsetDateTime to) {
        return analyticsRepository.getTopPeopleIReview(userId, login, from, to);
    }

    // ── Repo Commit Trend ─────────────────────────────────────────────────────

    @Override
    public List<CommitTrendDto> getCommitTrendByRepo(UUID repoId, OffsetDateTime from, OffsetDateTime to) {
        return analyticsRepository.getCommitTrendByRepo(repoId, from, to).stream()
                .map(r -> {
                    String date;
                    if (r[0] instanceof java.sql.Timestamp ts)
                        date = ts.toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString();
                    else if (r[0] instanceof java.time.Instant inst)
                        date = inst.atOffset(ZoneOffset.UTC).toLocalDate().toString();
                    else
                        date = r[0].toString().substring(0, 10);
                    return new CommitTrendDto(date, ((Number) r[1]).longValue());
                })
                .toList();
    }
}
