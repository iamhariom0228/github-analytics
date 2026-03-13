package com.gitanalytics.digest.controller;

import com.gitanalytics.digest.dto.DigestPreferencesDto;
import com.gitanalytics.digest.service.DigestService;
import com.gitanalytics.shared.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/digest")
@RequiredArgsConstructor
public class DigestController {

    private final DigestService digestService;

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<DigestPreferencesDto>> getPreferences(
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(digestService.getPreferences(userId)));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<DigestPreferencesDto>> updatePreferences(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody DigestPreferencesDto dto) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(digestService.updatePreferences(userId, dto)));
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<Void>> sendPreview(
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        digestService.sendPreviewDigest(userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
