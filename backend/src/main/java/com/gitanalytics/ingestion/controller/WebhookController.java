package com.gitanalytics.ingestion.controller;

import com.gitanalytics.ingestion.repository.TrackedRepoRepository;
import com.gitanalytics.ingestion.service.WebhookService;
import com.gitanalytics.shared.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/github")
    public ResponseEntity<ApiResponse<Void>> handleGitHubWebhook(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestBody String payload) {

        webhookService.processWebhook(event, signature, deliveryId, payload);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
