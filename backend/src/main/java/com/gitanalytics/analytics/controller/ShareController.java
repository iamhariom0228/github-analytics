package com.gitanalytics.analytics.controller;

import com.gitanalytics.analytics.service.ShareService;
import com.gitanalytics.shared.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping("/analytics/share")
    public ResponseEntity<ApiResponse<ShareService.CreateShareResponse>> createShare(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "UTC") String timezone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(shareService.createSnapshot(userId, timezone, from, to)));
    }

    @GetMapping("/public/share/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getShare(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(shareService.getSnapshot(token)));
    }
}
