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
import java.util.UUID;

@RestController
@RequestMapping("/repos")
@RequiredArgsConstructor
public class RepoController {

    private final RepoService repoService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RepoDto>>> getRepos(
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(repoService.getUserRepos(userId)));
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

    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<RepoSuggestionDto>>> getSuggestions(
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        List<RepoSuggestionDto> suggestions = repoService.getSuggestions(userId).stream()
            .map(r -> {
                String ownerLogin = r.getOwner() != null ? r.getOwner().getLogin() : null;
                String fullName = r.getFullName() != null ? r.getFullName()
                    : (ownerLogin != null ? ownerLogin + "/" + r.getName() : r.getName());
                return new RepoSuggestionDto(r.getId(), r.getName(), fullName, r.isPrivateRepo(), ownerLogin);
            })
            .toList();
        return ResponseEntity.ok(ApiResponse.ok(suggestions));
    }
}
