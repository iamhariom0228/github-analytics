package com.gitanalytics.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitanalytics.analytics.entity.SharedSnapshot;
import com.gitanalytics.analytics.repository.SharedSnapshotRepository;
import com.gitanalytics.auth.dao.UserDao;
import com.gitanalytics.auth.entity.User;
import com.gitanalytics.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private final SharedSnapshotRepository snapshotRepository;
    private final UserDao userDao;
    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    private static final SecureRandom RANDOM = new SecureRandom();

    public record CreateShareResponse(String token, String url) {}

    public CreateShareResponse createSnapshot(UUID userId, String timezone,
                                               OffsetDateTime from, OffsetDateTime to) {
        User user = userDao.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        OffsetDateTime effectiveTo = to != null ? to : OffsetDateTime.now();
        OffsetDateTime effectiveFrom = from != null ? from : effectiveTo.minusDays(30);

        String login = user.getUsername();

        // Build payload with available analytics data
        Map<String, Object> payload = buildPayload(userId, login, timezone, effectiveFrom, effectiveTo);

        String token = generateToken();

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            SharedSnapshot snapshot = SharedSnapshot.builder()
                .token(token)
                .user(user)
                .payload(payloadJson)
                .build();
            snapshotRepository.save(snapshot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create snapshot: " + e.getMessage(), e);
        }

        return new CreateShareResponse(token, "/share/" + token);
    }

    public Map<String, Object> getSnapshot(String token) {
        SharedSnapshot snapshot = snapshotRepository
            .findByTokenAndExpiresAtAfter(token, OffsetDateTime.now())
            .orElseThrow(() -> new ResourceNotFoundException("Snapshot not found or expired"));

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(snapshot.getPayload(), Map.class);
            return payload;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read snapshot payload", e);
        }
    }

    private Map<String, Object> buildPayload(UUID userId, String login, String timezone,
                                              OffsetDateTime from, OffsetDateTime to) {
        try {
            var overview = analyticsService.getOverview(userId, login, from, to);
            var streak = analyticsService.getStreak(userId, login, timezone != null ? timezone : "UTC");

            return Map.of(
                "login", login,
                "periodFrom", from.toString(),
                "periodTo", to.toString(),
                "commits", overview.commits(),
                "prsAuthored", overview.prsAuthored(),
                "reviewsGiven", overview.reviewsGiven(),
                "linesAdded", overview.linesAdded(),
                "linesRemoved", overview.linesRemoved(),
                "currentStreak", streak.getCurrentStreak(),
                "longestStreak", streak.getLongestStreak()
            );
        } catch (Exception e) {
            log.warn("Could not build full payload for {}: {}", login, e.getMessage());
            return Map.of("login", login, "error", "Some data unavailable");
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
