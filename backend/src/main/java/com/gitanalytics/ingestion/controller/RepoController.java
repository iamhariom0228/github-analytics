package com.gitanalytics.ingestion.controller;

import com.gitanalytics.ingestion.dto.AddRepoRequest;
import com.gitanalytics.ingestion.dto.RepoDto;
import com.gitanalytics.ingestion.dto.RepoSuggestionDto;
import com.gitanalytics.ingestion.dto.SyncStatusDto;
import com.gitanalytics.ingestion.service.RepoService;
import com.gitanalytics.shared.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/repos")
@RequiredArgsConstructor
public class RepoController {

    private final RepoService repoService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RepoDto>>> getRepos(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID userId = UUID.fromString(principal.getUsername());
        int clampedSize = Math.min(size, 100);
        return ResponseEntity.ok(ApiResponse.ok(repoService.getUserRepos(userId, page, clampedSize)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RepoDto>> addRepo(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody AddRepoRequest request) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(repoService.addRepo(userId, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRepo(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(principal.getUsername());
        repoService.deleteRepo(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<ApiResponse<SyncStatusDto>> triggerSync(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(repoService.triggerManualSync(userId, id)));
    }

    @GetMapping("/{id}/sync-status")
    public ResponseEntity<ApiResponse<SyncStatusDto>> getSyncStatus(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(repoService.getSyncStatus(userId, id)));
    }

    @PostMapping("/fork")
    public ResponseEntity<ApiResponse<Map<String, String>>> forkRepo(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody Map<String, String> request) {
        UUID userId = UUID.fromString(principal.getUsername());
        String owner = request.get("owner");
        String repo = request.get("repo");
        if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
            throw new IllegalArgumentException("owner and repo are required");
        }
        String htmlUrl = repoService.forkRepo(userId, owner, repo);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("htmlUrl", htmlUrl)));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<RepoSuggestionDto>>> getSuggestions(
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        List<RepoSuggestionDto> suggestions = repoService.getSuggestions(userId).stream()
            .map(r -> {
                String ownerLogin = r.getOwner() != null ? r.getOwner().getLogin() : null;
                String fullName = r.getFullName() != null ? r.getFullName()
                    : (ownerLogin != null ? ownerLogin + "/" + r.getName() : r.getName());
                return new RepoSuggestionDto(r.getId(), r.getName(), fullName, r.isPrivateRepo(),
                    ownerLogin != null ? new RepoSuggestionDto.Owner(ownerLogin) : null);
            })
            .toList();
        return ResponseEntity.ok(ApiResponse.ok(suggestions));
    }
}
