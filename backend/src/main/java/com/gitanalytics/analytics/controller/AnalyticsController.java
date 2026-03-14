package com.gitanalytics.analytics.controller;

import com.gitanalytics.analytics.dto.*;
import com.gitanalytics.analytics.service.AnalyticsService;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.shared.exception.ResourceNotFoundException;
import com.gitanalytics.shared.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final UserRepository userRepository;

    @GetMapping("/commits/heatmap")
    public ResponseEntity<ApiResponse<List<HeatmapCellDto>>> getHeatmap(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) String repoId,
            @RequestParam(defaultValue = "UTC") String timezone) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getCommitHeatmap(userId, repoId, timezone)));
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
        String login = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found")).getUsername();
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getReviewsSummary(userId, login, from, to)));
    }

    @GetMapping("/streak")
    public ResponseEntity<ApiResponse<StreakDto>> getStreak(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "UTC") String timezone) {
        UUID userId = UUID.fromString(principal.getUsername());
        String login = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found")).getUsername();
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
}
