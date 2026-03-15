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
                                 int streak, long prsMerged, double avgMergeHours, String aiSummary) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("RESEND_API_KEY not configured — skipping email to {}", toEmail);
            return;
        }

        String html = buildDigestHtml(username, weekStart, streak, prsMerged, avgMergeHours, aiSummary);
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
                                    long prsMerged, double avgMergeHours, String aiSummary) {
        String aiBlock = (aiSummary != null && !aiSummary.isBlank()) ? """
              <div style="background:#f0f6ff;border-left:4px solid #0969da;padding:16px;border-radius:0 6px 6px 0;margin:20px 0;">
                <div style="font-size:11px;font-weight:600;color:#0969da;letter-spacing:0.05em;margin-bottom:8px;">✦ AI COACHING INSIGHT</div>
                <p style="margin:0;color:#1f2937;font-size:14px;line-height:1.6;">%s</p>
              </div>
            """.formatted(aiSummary) : "";

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; background: #f9fafb; }
                .card { background: #fff; border-radius: 12px; padding: 24px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
                .header { background: #0d1117; color: #fff; padding: 28px; border-radius: 12px; }
                .header h1 { margin: 0 0 4px; font-size: 22px; }
                .header p { margin: 0; color: #8b949e; font-size: 14px; }
                .metrics { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; }
                .metric { background: #f6f8fa; padding: 16px; border-radius: 8px; text-align: center; }
                .metric-value { font-size: 28px; font-weight: 700; color: #0d1117; }
                .metric-label { color: #656d76; font-size: 12px; margin-top: 2px; }
                .footer { color: #9ca3af; font-size: 12px; text-align: center; margin-top: 20px; }
              </style>
            </head>
            <body>
              <div class="card header">
                <h1>GitHub Analytics</h1>
                <p>Weekly digest for <strong style="color:#fff">%s</strong> &mdash; week of %s</p>
              </div>
              <div class="card">
                <div class="metrics">
                  <div class="metric">
                    <div class="metric-value">%d</div>
                    <div class="metric-label">Day streak</div>
                  </div>
                  <div class="metric">
                    <div class="metric-value">%d</div>
                    <div class="metric-label">PRs merged</div>
                  </div>
                  <div class="metric">
                    <div class="metric-value">%.1fh</div>
                    <div class="metric-label">Avg merge time</div>
                  </div>
                </div>
                %s
              </div>
              <div class="footer">
                <p>You're receiving this because you enabled weekly digests on GitHub Analytics.</p>
              </div>
            </body>
            </html>
            """.formatted(username, weekStart, streak, prsMerged, avgMergeHours, aiBlock);
    }
}
