package com.gitanalytics.analytics.controller;

import com.gitanalytics.analytics.dto.*;
import com.gitanalytics.analytics.service.AnalyticsService;
import com.gitanalytics.shared.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    private String getLogin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof UsernamePasswordAuthenticationToken upat
                && upat.getDetails() instanceof String login) {
            return login;
        }
        // fallback (shouldn't happen for authenticated requests)
        throw new IllegalStateException("Username not available in security context");
    }

    @GetMapping("/commits/heatmap")
    public ResponseEntity<ApiResponse<List<HeatmapCellDto>>> getHeatmap(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) String repoId,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getCommitHeatmap(userId, repoId, timezone, from, to)));
    }

    @GetMapping("/prs/lifecycle")
    public ResponseEntity<ApiResponse<PRLifecycleDto>> getPRLifecycle(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getPRLifecycle(userId, from, to)));
    }

    @GetMapping("/prs/size-distribution")
    public ResponseEntity<ApiResponse<PRSizeDistributionDto>> getSizeDistribution(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getPRSizeDistribution(userId, from, to)));
    }

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<ReviewsSummaryDto>> getReviewsSummary(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        String login = getLogin();
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getReviewsSummary(userId, login, from, to)));
    }

    @GetMapping("/streak")
    public ResponseEntity<ApiResponse<StreakDto>> getStreak(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "UTC") String timezone) {
        UUID userId = UUID.fromString(principal.getUsername());
        String login = getLogin();
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getStreak(userId, login, timezone)));
    }

    @GetMapping("/team/leaderboard")
    public ResponseEntity<ApiResponse<List<ContributorStatsDto>>> getLeaderboard(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam UUID repoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getTeamLeaderboard(userId, repoId, from, to)));
    }

    @GetMapping("/team/bus-factor")
    public ResponseEntity<ApiResponse<BusFactorDto>> getBusFactor(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam UUID repoId) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getBusFactor(userId, repoId)));
    }

    @GetMapping("/team/stale-prs")
    public ResponseEntity<ApiResponse<List<PrSummaryDto>>> getStalePRs(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam UUID repoId,
            @RequestParam(defaultValue = "7") int olderThanDays) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getStalePRs(userId, repoId, olderThanDays)));
    }

    @GetMapping("/ai-summary")
    public ResponseEntity<ApiResponse<AnalyticsService.AiSummaryDto>> getAiSummary(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        String login = getLogin();
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getAiSummary(userId, login, timezone, from, to)));
    }

    @GetMapping("/repos/{repoId}/health")
    public ResponseEntity<ApiResponse<RepoHealthDto>> getRepoHealth(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID repoId) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getRepoHealth(userId, repoId)));
    }

    @GetMapping("/repos/{repoId}/stats")
    public ResponseEntity<ApiResponse<PublicRepoStatsDto>> getRepoStats(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID repoId) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getPublicRepoStats(userId, repoId)));
    }

    @GetMapping("/commits/trend")
    public ResponseEntity<ApiResponse<List<CommitTrendDto>>> getCommitTrend(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "daily") String granularity,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        String login = getLogin();
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getCommitTrend(userId, login, granularity, from, to)));
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<OverviewDto>> getOverview(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        String login = getLogin();
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getOverview(userId, login, from, to)));
    }

    @GetMapping("/insights")
    public ResponseEntity<ApiResponse<List<InsightDto>>> getInsights(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        String login = getLogin();
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getInsights(userId, login, timezone, from, to)));
    }

    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<List<ActivityEvent>>> getActivityFeed(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "30") int limit) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getActivityFeed(userId, limit)));
    }

    @GetMapping("/review-queue")
    public ResponseEntity<ApiResponse<List<ReviewQueueItem>>> getReviewQueue(
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getReviewQueue(userId)));
    }

    @GetMapping("/prs/merge-rate-trend")
    public ResponseEntity<ApiResponse<List<PRMergeRateDto>>> getPRMergeRateTrend(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        String login = getLogin();
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getPRMergeRateTrend(userId, login, from, to)));
    }

    @GetMapping("/prs/reviewer-coverage")
    public ResponseEntity<ApiResponse<ReviewerCoverageDto>> getReviewerCoverage(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        String login = getLogin();
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getReviewerCoverage(userId, login, from, to)));
    }

    @GetMapping("/team/churn")
    public ResponseEntity<ApiResponse<List<ContributorStatsDto>>> getChurnLeaderboard(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam UUID repoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getChurnLeaderboard(userId, repoId, from, to)));
    }

    @GetMapping("/collaboration")
    public ResponseEntity<ApiResponse<CollaborationDto>> getCollaboration(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        String login = getLogin();
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getCollaboration(userId, login, from, to)));
    }

    @GetMapping("/repos/{repoId}/commit-trend")
    public ResponseEntity<ApiResponse<List<CommitTrendDto>>> getRepoCommitTrend(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID repoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getCommitTrendByRepo(userId, repoId, from, to)));
    }

    @GetMapping("/repos/{repoId}/stars-forks-trend")
    public ResponseEntity<ApiResponse<List<StarsForksSnapshotDto>>> getStarsForksTrend(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID repoId) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getStarsForksTrend(userId, repoId)));
    }

    @GetMapping("/repos/{repoId}/release-trend")
    public ResponseEntity<ApiResponse<List<ReleaseTrendDto>>> getReleaseTrend(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID repoId) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getReleaseTrend(userId, repoId)));
    }

    @GetMapping("/repos/{repoId}/issues")
    public ResponseEntity<ApiResponse<IssueAnalyticsDto>> getIssueAnalytics(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID repoId) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getIssueAnalytics(userId, repoId)));
    }

    @GetMapping("/repos/{repoId}/language-bytes")
    public ResponseEntity<ApiResponse<List<RepoLanguageDto>>> getLanguageBytes(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID repoId) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getLanguageBytes(userId, repoId)));
    }

    @GetMapping("/compare/repos")
    public ResponseEntity<ApiResponse<List<RepoCompareDto>>> compareRepos(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam List<UUID> repoIds) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.compareRepos(userId, repoIds)));
    }

    @GetMapping("/compare/contributors")
    public ResponseEntity<ApiResponse<List<ContributorCompareDto>>> compareContributors(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam List<String> logins) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.compareContributors(userId, logins)));
    }
}
