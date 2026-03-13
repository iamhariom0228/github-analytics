package com.gitanalytics.digest.service;

import com.gitanalytics.analytics.service.AnalyticsService;
import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.entity.UserPreferences;
import com.gitanalytics.auth.repository.UserPreferencesRepository;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.digest.dto.DigestPreferencesDto;
import com.gitanalytics.digest.entity.DigestLog;
import com.gitanalytics.digest.repository.DigestLogRepository;
import com.gitanalytics.notification.service.EmailService;
import com.gitanalytics.shared.exception.ResourceNotFoundException;
import com.gitanalytics.shared.kafka.events.DigestTriggerEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DigestService {

    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final DigestLogRepository digestLogRepository;
    private final AnalyticsService analyticsService;
    private final EmailService emailService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Run every hour — find users whose digest time matches now
    @Scheduled(cron = "0 0 * * * *")
    public void scheduleDigests() {
        List<UserPreferences> prefs = userPreferencesRepository.findAll();
        for (UserPreferences pref : prefs) {
            if (!pref.isDigestEnabled()) continue;
            try {
                ZoneId tz = ZoneId.of(pref.getTimezone());
                ZonedDateTime now = ZonedDateTime.now(tz);
                if (now.getDayOfWeek().getValue() == pref.getDigestDayOfWeek()
                        && now.getHour() == pref.getDigestHour()) {
                    LocalDate weekStart = now.toLocalDate().minusDays(7);
                    if (!digestLogRepository.existsByUserIdAndWeekStart(pref.getUser().getId(), weekStart)) {
                        kafkaTemplate.send("ga.digest.trigger",
                            pref.getUser().getId().toString(),
                            new DigestTriggerEvent(pref.getUser().getId(), weekStart));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to schedule digest for user {}: {}", pref.getUser().getId(), e.getMessage());
            }
        }
    }

    @KafkaListener(topics = "ga.digest.trigger", groupId = "github-analytics")
    @Transactional
    public void handleDigestTrigger(DigestTriggerEvent event) {
        sendDigest(event.getUserId(), event.getWeekStart(), false);
    }

    @Transactional
    public void sendPreviewDigest(UUID userId) {
        LocalDate weekStart = LocalDate.now().minusDays(7);
        sendDigest(userId, weekStart, true);
    }

    private void sendDigest(UUID userId, LocalDate weekStart, boolean preview) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("No email for user {} — skipping digest", userId);
            return;
        }

        UserPreferences prefs = userPreferencesRepository.findByUserId(userId).orElse(null);
        String tz = prefs != null ? prefs.getTimezone() : "UTC";

        OffsetDateTime from = weekStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = from.plusWeeks(1);

        var streak = analyticsService.getStreak(userId, user.getUsername(), tz);
        var lifecycle = analyticsService.getPRLifecycle(userId, from, to);

        emailService.sendDigestEmail(user.getEmail(), user.getUsername(), weekStart,
            streak.getCurrentStreak(), lifecycle.getMergedCount(), lifecycle.getAvgHoursToMerge());

        if (!preview) {
            digestLogRepository.save(DigestLog.builder()
                .user(user)
                .weekStart(weekStart)
                .sentAt(OffsetDateTime.now())
                .build());
        }
    }

    public DigestPreferencesDto getPreferences(UUID userId) {
        UserPreferences prefs = userPreferencesRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Preferences not found"));
        return new DigestPreferencesDto(prefs.isDigestEnabled(), prefs.getDigestDayOfWeek(),
            prefs.getDigestHour(), prefs.getTimezone());
    }

    @Transactional
    public DigestPreferencesDto updatePreferences(UUID userId, DigestPreferencesDto dto) {
        UserPreferences prefs = userPreferencesRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Preferences not found"));
        prefs.setDigestEnabled(dto.isDigestEnabled());
        prefs.setDigestDayOfWeek(dto.getDigestDayOfWeek());
        prefs.setDigestHour(dto.getDigestHour());
        prefs.setTimezone(dto.getTimezone());
        userPreferencesRepository.save(prefs);
        return dto;
    }
}
