package com.gitanalytics.analytics.dao;

import com.gitanalytics.analytics.dto.CommitTrendDto;
import com.gitanalytics.analytics.dto.ContributorStatsDto;
import com.gitanalytics.analytics.dto.HeatmapCellDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AnalyticsDao {

    // Heatmap
    List<HeatmapCellDto> getCommitHeatmap(UUID userId, String timezone, UUID repoId,
                                           OffsetDateTime from, OffsetDateTime to);

    // PR lifecycle — returns [avg_to_review, avg_to_merge, merged_count, total_count]
    Object[] getPRLifecycleStats(UUID userId, OffsetDateTime from, OffsetDateTime to);

    // Team leaderboard
    List<ContributorStatsDto> getTeamLeaderboard(UUID userId, UUID repoId,
                                                  OffsetDateTime from, OffsetDateTime to);

    // Bus factor — rows of [author_login, commit_count]
    List<Object[]> getBusFactorRows(UUID userId, UUID repoId);

    // Reviews summary — [total, approved, changes_requested, commented, prs_reviewed]
    Object[] getReviewsSummaryRow(UUID userId, String login, OffsetDateTime from, OffsetDateTime to);

    // Repo health signals
    long countRecentCommits(UUID repoId, OffsetDateTime since);
    Object[] getPRReviewCoverage(UUID repoId, OffsetDateTime since);
    List<Object[]> getTopAuthorRows(UUID repoId, OffsetDateTime since);
    Double getAvgMergeTimeHours(UUID repoId, OffsetDateTime since);
    long countStalePRsByRepo(UUID repoId, OffsetDateTime before);

    // Commit trend
    List<CommitTrendDto> getCommitTrend(UUID userId, String login, String granularity,
                                         OffsetDateTime from, OffsetDateTime to);

    // Overview / shared helpers
    long countCommits(UUID userId, String login, OffsetDateTime from, OffsetDateTime to);
    long countPRsAuthored(UUID userId, String login, OffsetDateTime from, OffsetDateTime to);
    long countReviewsGiven(UUID userId, String login, OffsetDateTime from, OffsetDateTime to);
    Object[] getLinesAddedRemoved(UUID userId, String login, OffsetDateTime from, OffsetDateTime to);

    // Insights
    Object[] getPeakCodingTime(UUID userId, String login, String timezone);
    Object[] getWeekdayWeekendSplit(UUID userId, String login, String timezone);
    Object[] getCommitTrendComparison(UUID userId, String login,
                                       OffsetDateTime from, OffsetDateTime to,
                                       OffsetDateTime prevFrom, OffsetDateTime prevTo);
    long countOpenStalePRsForUser(UUID userId);
    Double getAvgMergeTimeForUser(UUID userId, String login, OffsetDateTime from, OffsetDateTime to);
    Object[] getReviewStyleStats(UUID userId, String login, OffsetDateTime from, OffsetDateTime to);
    long countTotalCommitsForUser(UUID userId, String login);

    // Streak
    Object[] getStreakData(UUID userId, String login, String timezone);

    // Public repo stats
    long countCommitsByRepo(UUID repoId);
    Object[] getPRStatsByRepo(UUID repoId);
    List<Object[]> getTopContributorByRepo(UUID repoId);

    // AI summary helper
    long countOpenStalePRsOlderThan(UUID userId, String intervalDays);
}
