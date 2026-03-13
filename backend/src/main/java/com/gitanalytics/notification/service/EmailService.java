package com.gitanalytics.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final WebClient.Builder webClientBuilder;

    @Value("${RESEND_API_KEY:}")
    private String resendApiKey;

    @Value("${RESEND_FROM_EMAIL:digest@gitanalytics.dev}")
    private String fromEmail;

    public void sendDigestEmail(String toEmail, String username, LocalDate weekStart,
                                 int streak, long prsMerged, double avgMergeHours) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("RESEND_API_KEY not configured — skipping email to {}", toEmail);
            return;
        }

        String html = buildDigestHtml(username, weekStart, streak, prsMerged, avgMergeHours);
        String subject = String.format("Your GitHub Analytics Weekly Digest — %s", weekStart);

        try {
            webClientBuilder.build()
                .post()
                .uri("https://api.resend.com/emails")
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(Map.of(
                    "from", fromEmail,
                    "to", new String[]{toEmail},
                    "subject", subject,
                    "html", html
                ))
                .retrieve()
                .bodyToMono(String.class)
                .block();
            log.info("Digest email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildDigestHtml(String username, LocalDate weekStart, int streak,
                                    long prsMerged, double avgMergeHours) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <style>
                body { font-family: -apple-system, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; }
                .header { background: #0d1117; color: #fff; padding: 24px; border-radius: 8px; }
                .metric { background: #f6f8fa; padding: 16px; border-radius: 6px; margin: 12px 0; }
                .metric-value { font-size: 32px; font-weight: bold; color: #0d1117; }
                .metric-label { color: #656d76; font-size: 14px; }
                .footer { color: #656d76; font-size: 12px; margin-top: 24px; }
              </style>
            </head>
            <body>
              <div class="header">
                <h1>GitHub Analytics</h1>
                <p>Weekly digest for <strong>%s</strong> — week of %s</p>
              </div>
              <div class="metric">
                <div class="metric-value">%d</div>
                <div class="metric-label">Current contribution streak (days)</div>
              </div>
              <div class="metric">
                <div class="metric-value">%d</div>
                <div class="metric-label">Pull requests merged this week</div>
              </div>
              <div class="metric">
                <div class="metric-value">%.1fh</div>
                <div class="metric-label">Average time to merge</div>
              </div>
              <div class="footer">
                <p>You're receiving this because you enabled weekly digests on GitHub Analytics.</p>
              </div>
            </body>
            </html>
            """.formatted(username, weekStart, streak, prsMerged, avgMergeHours);
    }
}
