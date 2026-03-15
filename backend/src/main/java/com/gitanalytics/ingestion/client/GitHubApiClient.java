package com.gitanalytics.ingestion.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitanalytics.shared.config.AppProperties;
import com.gitanalytics.shared.exception.GitHubApiException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubApiClient {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final int PER_PAGE = 100;
    private static final int RATE_LIMIT_BUFFER = 100;

    private final WebClient.Builder webClientBuilder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public List<GitHubRepoDto> getUserRepos(String accessToken, UUID userId) {
        List<GitHubRepoDto> all = new ArrayList<>();
        int page = 1;
        while (true) {
            List<GitHubRepoDto> page_ = get(
                accessToken, userId,
                "/user/repos?per_page=" + PER_PAGE + "&page=" + page + "&sort=updated",
                GitHubRepoDto.class
            );
            if (page_.isEmpty()) break;
            all.addAll(page_);
            if (page_.size() < PER_PAGE) break;
            page++;
        }
        return all;
    }

    public List<GitHubCommitDto> getCommits(String accessToken, UUID userId,
                                             String owner, String repo,
                                             OffsetDateTime since) {
        List<GitHubCommitDto> all = new ArrayList<>();
        int page = 1;
        String sinceParam = since != null ? "&since=" + since.toString() : "";
        while (true) {
            List<GitHubCommitDto> batch = get(
                accessToken, userId,
                "/repos/" + owner + "/" + repo + "/commits?per_page=" + PER_PAGE
                    + "&page=" + page + sinceParam,
                GitHubCommitDto.class
            );
            if (batch.isEmpty()) break;
            all.addAll(batch);
            if (batch.size() < PER_PAGE) break;
            page++;
        }
        return all;
    }

    public List<GitHubPRDto> getPullRequests(String accessToken, UUID userId,
                                              String owner, String repo,
                                              OffsetDateTime since) {
        List<GitHubPRDto> all = new ArrayList<>();
        int page = 1;
        while (true) {
            List<GitHubPRDto> batch = get(
                accessToken, userId,
                "/repos/" + owner + "/" + repo + "/pulls?state=all&per_page=" + PER_PAGE + "&page=" + page,
                GitHubPRDto.class
            );
            if (batch.isEmpty()) break;
            // Filter by since if provided
            if (since != null) {
                batch = batch.stream()
                    .filter(pr -> pr.getCreatedAt() != null && pr.getCreatedAt().isAfter(since))
                    .toList();
                if (batch.isEmpty()) break;
            }
            all.addAll(batch);
            if (batch.size() < PER_PAGE) break;
            page++;
        }
        return all;
    }

    public GitHubCommitDetailDto getCommitDetail(String accessToken, UUID userId,
                                                  String owner, String repo, String sha) {
        checkRateLimit(userId);
        try {
            return webClientBuilder.build()
                .get()
                .uri(GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/commits/" + sha)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .exchangeToMono(resp -> {
                    String remaining = resp.headers().asHttpHeaders().getFirst("X-RateLimit-Remaining");
                    if (remaining != null) {
                        redisTemplate.opsForValue().set("ga:ratelimit:gh:" + userId, remaining, 1, TimeUnit.HOURS);
                    }
                    return resp.bodyToMono(GitHubCommitDetailDto.class);
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.warn("Failed to get commit detail {}: {}", sha, e.getStatusCode());
                    return Mono.empty();
                })
                .block();
        } catch (Exception e) {
            log.warn("Could not fetch commit detail for {}: {}", sha, e.getMessage());
            return null;
        }
    }

    public List<GitHubReviewDto> getReviews(String accessToken, UUID userId,
                                             String owner, String repo, int prNumber) {
        return get(accessToken, userId,
            "/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/reviews?per_page=100",
            GitHubReviewDto.class);
    }

    public GitHubRepoDto createWebhook(String accessToken, UUID userId,
                                        String owner, String repo, String webhookUrl, String secret) {
        return webClientBuilder.build()
            .post()
            .uri(GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/hooks")
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/vnd.github+json")
            .bodyValue(Map.of(
                "name", "web",
                "active", true,
                "events", List.of("push", "pull_request", "pull_request_review"),
                "config", Map.of(
                    "url", webhookUrl,
                    "content_type", "json",
                    "secret", secret
                )
            ))
            .retrieve()
            .bodyToMono(GitHubRepoDto.class)
            .block();
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> get(String accessToken, UUID userId, String path, Class<T> type) {
        checkRateLimit(userId);
        try {
            List<?> raw = webClientBuilder.build()
                .get()
                .uri(GITHUB_API_BASE + path)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .exchangeToMono(resp -> {
                    String remaining = resp.headers().asHttpHeaders().getFirst("X-RateLimit-Remaining");
                    if (remaining != null) {
                        redisTemplate.opsForValue().set(
                            "ga:ratelimit:gh:" + userId,
                            remaining,
                            1, TimeUnit.HOURS
                        );
                    }
                    return resp.bodyToMono(List.class);
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("GitHub API error {} for {}: {}", e.getStatusCode(), path, e.getMessage());
                    return Mono.error(new GitHubApiException("GitHub API returned " + e.getStatusCode()));
                })
                .block();
            if (raw == null) return List.of();
            return raw.stream()
                .map(item -> objectMapper.convertValue(item, type))
                .toList();
        } catch (GitHubApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubApiException("GitHub API call failed: " + e.getMessage(), e);
        }
    }

    private void checkRateLimit(UUID userId) {
        Object remaining = redisTemplate.opsForValue().get("ga:ratelimit:gh:" + userId);
        if (remaining != null) {
            try {
                int rem = Integer.parseInt(remaining.toString());
                if (rem < RATE_LIMIT_BUFFER) {
                    throw new GitHubApiException("GitHub rate limit nearly exhausted: " + rem + " remaining");
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    // --- DTOs ---

    @Data
    public static class GitHubRepoDto {
        private Long id;
        private String name;
        @JsonProperty("full_name")
        private String fullName;
        @JsonProperty("private")
        private boolean privateRepo;
        private OwnerDto owner;

        @Data
        public static class OwnerDto {
            private String login;
        }
    }

    @Data
    public static class GitHubCommitDto {
        private String sha;
        private CommitDetail commit;
        private AuthorDto author;

        @Data
        public static class CommitDetail {
            private AuthorDetail author;
            private String message;

            @Data
            public static class AuthorDetail {
                private String name;
                private OffsetDateTime date;
            }
        }

        @Data
        public static class AuthorDto {
            private Long id;
            private String login;
        }
    }

    @Data
    public static class GitHubCommitDetailDto {
        private String sha;
        private CommitDetail commit;
        private AuthorDto author;
        private StatsDto stats;

        @Data
        public static class StatsDto {
            private int additions;
            private int deletions;
            private int total;
        }

        @Data
        public static class CommitDetail {
            private AuthorDetail author;

            @Data
            public static class AuthorDetail {
                private String name;
                private OffsetDateTime date;
            }
        }

        @Data
        public static class AuthorDto {
            private Long id;
            private String login;
        }
    }

    @Data
    public static class GitHubPRDto {
        private Long id;
        private Integer number;
        private String title;
        private String state;
        @JsonProperty("created_at")
        private OffsetDateTime createdAt;
        @JsonProperty("merged_at")
        private OffsetDateTime mergedAt;
        @JsonProperty("closed_at")
        private OffsetDateTime closedAt;
        private UserDto user;
        private int additions;
        private int deletions;
        @JsonProperty("changed_files")
        private int changedFiles;

        @Data
        public static class UserDto {
            private Long id;
            private String login;
        }
    }

    @Data
    public static class GitHubReviewDto {
        private Long id;
        private UserDto user;
        private String state;
        @JsonProperty("submitted_at")
        private OffsetDateTime submittedAt;

        @Data
        public static class UserDto {
            private String login;
        }
    }
}
