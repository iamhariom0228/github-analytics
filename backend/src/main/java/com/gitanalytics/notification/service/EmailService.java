package com.gitanalytics.notification.service;

import com.gitanalytics.analytics.dto.OverviewDto;
import com.gitanalytics.analytics.dto.ReviewQueueItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.List;
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
                                 int streak, long prsMerged, double avgMergeHours, String aiSummary,
                                 List<Long> sparkline, List<ReviewQueueItem> top3Prs, OverviewDto monthOverview) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("RESEND_API_KEY not configured — skipping email to {}", toEmail);
            return;
        }

        String html = buildDigestHtml(username, weekStart, streak, prsMerged, avgMergeHours,
                aiSummary, sparkline, top3Prs, monthOverview);
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
                                    long prsMerged, double avgMergeHours, String aiSummary,
                                    List<Long> sparkline, List<ReviewQueueItem> top3Prs,
                                    OverviewDto monthOverview) {
        String aiBlock = (aiSummary != null && !aiSummary.isBlank()) ? """
              <div style="background:#f0f6ff;border-left:4px solid #0969da;padding:16px;border-radius:0 6px 6px 0;margin:20px 0;">
                <div style="font-size:11px;font-weight:600;color:#0969da;letter-spacing:0.05em;margin-bottom:8px;">✦ AI COACHING INSIGHT</div>
                <p style="margin:0;color:#1f2937;font-size:14px;line-height:1.6;">%s</p>
              </div>
            """.formatted(aiSummary) : "";

        String sparklineBlock = buildSparklineBlock(sparkline);
        String top3Block = buildTop3PrsBlock(top3Prs);
        String goalBlock = buildGoalProgressBlock(monthOverview);

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
                .section-title { font-size: 13px; font-weight: 600; color: #0d1117; margin: 0 0 12px; }
                .sparkline { font-family: monospace; font-size: 22px; letter-spacing: 2px; color: #0969da; }
                .sparkline-label { font-size: 11px; color: #656d76; margin-top: 4px; }
                .pr-row { display: flex; align-items: flex-start; gap: 10px; padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
                .pr-row:last-child { border-bottom: none; }
                .pr-num { font-size: 11px; color: #656d76; white-space: nowrap; padding-top: 2px; }
                .pr-title { font-size: 13px; color: #0d1117; flex: 1; }
                .pr-meta { font-size: 11px; color: #656d76; margin-top: 2px; }
                .pr-badge { display: inline-block; font-size: 10px; font-weight: 600; padding: 1px 6px; border-radius: 4px; background: #f0f6ff; color: #0969da; }
                .goal-row { margin-bottom: 12px; }
                .goal-header { display: flex; justify-content: space-between; font-size: 12px; color: #656d76; margin-bottom: 4px; }
                .goal-bar-bg { height: 6px; background: #f0f0f0; border-radius: 3px; overflow: hidden; }
                .goal-bar-fill { height: 100%; border-radius: 3px; }
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
              %s
              %s
              %s
              <div class="footer">
                <p>You're receiving this because you enabled weekly digests on GitHub Analytics.</p>
              </div>
            </body>
            </html>
            """.formatted(username, weekStart, streak, prsMerged, avgMergeHours,
                aiBlock, sparklineBlock, top3Block, goalBlock);
    }

    private String buildSparklineBlock(List<Long> sparkline) {
        if (sparkline == null || sparkline.isEmpty()) return "";
        String[] blocks = {"▁", "▂", "▃", "▄", "▅", "▆", "▇", "█"};
        long max = sparkline.stream().mapToLong(Long::longValue).max().orElse(1);
        if (max == 0) max = 1;
        StringBuilder sb = new StringBuilder();
        for (Long count : sparkline) {
            int idx = (int) Math.round((double) count / max * (blocks.length - 1));
            sb.append(blocks[idx]);
        }
        long total = sparkline.stream().mapToLong(Long::longValue).sum();
        return """
            <div class="card">
              <div class="section-title">Commit Activity This Week</div>
              <div class="sparkline">%s</div>
              <div class="sparkline-label">%d commits over %d days</div>
            </div>
            """.formatted(sb.toString(), total, sparkline.size());
    }

    private String buildTop3PrsBlock(List<ReviewQueueItem> prs) {
        if (prs == null || prs.isEmpty()) return "";
        StringBuilder rows = new StringBuilder();
        for (ReviewQueueItem pr : prs) {
            long ageDays = pr.getAgeHours() / 24;
            String ageLabel = pr.getAgeHours() < 24
                    ? pr.getAgeHours() + "h old"
                    : ageDays + "d old";
            rows.append("""
                <div class="pr-row">
                  <div class="pr-num">#%d</div>
                  <div>
                    <div class="pr-title">%s</div>
                    <div class="pr-meta">%s &bull; by %s &bull; %s &bull; <span class="pr-badge">%s</span></div>
                  </div>
                </div>
                """.formatted(pr.getPrNumber(), escapeHtml(pr.getTitle()),
                    escapeHtml(pr.getRepoFullName()), escapeHtml(pr.getAuthorLogin()),
                    ageLabel, pr.getSizeLabel()));
        }
        return """
            <div class="card">
              <div class="section-title">PRs Waiting for Your Review (%d)</div>
              %s
            </div>
            """.formatted(prs.size(), rows.toString());
    }

    private String buildGoalProgressBlock(OverviewDto overview) {
        if (overview == null) return "";
        // Default monthly goals (mirrors frontend defaults)
        int targetCommits = 50, targetPrs = 10, targetReviews = 15;
        long targetLines = 2000;

        String commitBar = goalBar(overview.commits(), targetCommits, "#22c55e");
        String prBar = goalBar(overview.prsAuthored(), targetPrs, "#3b82f6");
        String reviewBar = goalBar(overview.reviewsGiven(), targetReviews, "#8b5cf6");
        String linesBar = goalBar(overview.linesAdded(), targetLines, "#ec4899");

        return """
            <div class="card">
              <div class="section-title">Monthly Goal Progress</div>
              <div class="goal-row">
                <div class="goal-header"><span>📦 Commits</span><span>%d / %d</span></div>
                %s
              </div>
              <div class="goal-row">
                <div class="goal-header"><span>🎯 Pull Requests</span><span>%d / %d</span></div>
                %s
              </div>
              <div class="goal-row">
                <div class="goal-header"><span>👁 Code Reviews</span><span>%d / %d</span></div>
                %s
              </div>
              <div class="goal-row">
                <div class="goal-header"><span>📝 Lines Added</span><span>%s / %s</span></div>
                %s
              </div>
            </div>
            """.formatted(
                overview.commits(), targetCommits, commitBar,
                overview.prsAuthored(), targetPrs, prBar,
                overview.reviewsGiven(), targetReviews, reviewBar,
                format(overview.linesAdded()), format(targetLines), linesBar);
    }

    private String goalBar(long current, long target, String color) {
        int pct = target > 0 ? (int) Math.min(100, Math.round((double) current / target * 100)) : 0;
        return """
            <div class="goal-bar-bg">
              <div class="goal-bar-fill" style="width:%d%%;background:%s;"></div>
            </div>
            """.formatted(pct, color);
    }

    private String format(long n) {
        if (n >= 1000) return String.format("%.1fk", n / 1000.0);
        return String.valueOf(n);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
