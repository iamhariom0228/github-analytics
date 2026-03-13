package com.gitanalytics.analytics.controller;

import com.gitanalytics.analytics.dto.DashboardSummaryDto;
import com.gitanalytics.analytics.service.AnalyticsService;
import com.gitanalytics.shared.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardSummaryDto>> getDashboard(
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getDashboard(userId)));
    }
}
