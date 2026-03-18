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

    public GitHubPRDetailDto getPullRequestDetail(String accessToken, UUID userId,
                                                   String owner, String repo, int prNumber) {
        checkRateLimit(userId);
        try {
            return webClientBuilder.build()
                .get()
                .uri(GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .exchangeToMono(resp -> {
                    String remaining = resp.headers().asHttpHeaders().getFirst("X-RateLimit-Remaining");
                    if (remaining != null) {
                        redisTemplate.opsForValue().set("ga:ratelimit:gh:" + userId, remaining, 1, TimeUnit.HOURS);
                    }
                    return resp.bodyToMono(GitHubPRDetailDto.class);
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.warn("Failed to get PR detail #{}: {}", prNumber, e.getStatusCode());
                    return Mono.empty();
                })
                .block();
        } catch (Exception e) {
            log.warn("Could not fetch PR detail #{}: {}", prNumber, e.getMessage());
            return null;
        }
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

    @SuppressWarnings("unchecked")
    public List<GraphQLCommitDto> getCommitsWithStats(String accessToken, UUID userId,
                                                       String owner, String repo,
                                                       OffsetDateTime since) {
        List<GraphQLCommitDto> all = new ArrayList<>();
        String cursor = null;

        while (true) {
            StringBuilder args = new StringBuilder("first: 100");
            if (since != null) args.append(", since: \"").append(since).append("\"");
            if (cursor != null) args.append(", after: \"").append(cursor).append("\"");

            String query = String.format(
                "{ repository(owner: \"%s\", name: \"%s\") { defaultBranchRef { target { " +
                "... on Commit { history(%s) { pageInfo { hasNextPage endCursor } " +
                "nodes { oid additions deletions committedDate message " +
                "author { email user { login databaseId } } " +
                "committer { user { login databaseId } } } } } } } } }",
                owner, repo, args);

            checkRateLimit(userId);
            try {
                Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(GITHUB_API_BASE + "/graphql")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .bodyValue(Map.of("query", query))
                    .exchangeToMono(resp -> {
                        String remaining = resp.headers().asHttpHeaders().getFirst("X-RateLimit-Remaining");
                        if (remaining != null) {
                            redisTemplate.opsForValue().set("ga:ratelimit:gh:" + userId, remaining, 1, TimeUnit.HOURS);
                        }
                        return resp.bodyToMono(Map.class);
                    })
                    .onErrorResume(WebClientResponseException.class, e -> {
                        log.error("GraphQL error: {}", e.getStatusCode());
                        return Mono.error(new GitHubApiException("GraphQL returned " + e.getStatusCode()));
                    })
                    .block();

                if (response == null) break;

                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data == null) break;
                Map<String, Object> repository = (Map<String, Object>) data.get("repository");
                if (repository == null) break;
                Map<String, Object> defaultBranchRef = (Map<String, Object>) repository.get("defaultBranchRef");
                if (defaultBranchRef == null) break;
                Map<String, Object> target = (Map<String, Object>) defaultBranchRef.get("target");
                if (target == null) break;
                Map<String, Object> history = (Map<String, Object>) target.get("history");
                if (history == null) break;

                Map<String, Object> pageInfo = (Map<String, Object>) history.get("pageInfo");
                boolean hasNextPage = Boolean.TRUE.equals(pageInfo.get("hasNextPage"));
                cursor = (String) pageInfo.get("endCursor");

                List<Map<String, Object>> nodes = (List<Map<String, Object>>) history.get("nodes");
                if (nodes == null || nodes.isEmpty()) break;

                for (Map<String, Object> node : nodes) {
                    GraphQLCommitDto dto = new GraphQLCommitDto();
                    dto.setSha((String) node.get("oid"));
                    dto.setAdditions(((Number) node.getOrDefault("additions", 0)).intValue());
                    dto.setDeletions(((Number) node.getOrDefault("deletions", 0)).intValue());
                    dto.setMessage((String) node.get("message"));
                    String dateStr = (String) node.get("committedDate");
                    if (dateStr != null) dto.setCommittedDate(OffsetDateTime.parse(dateStr));

                    Map<String, Object> author = (Map<String, Object>) node.get("author");
                    if (author != null) {
                        dto.setAuthorEmail((String) author.get("email"));
                        Map<String, Object> authorUser = (Map<String, Object>) author.get("user");
                        if (authorUser != null) {
                            dto.setAuthorLogin((String) authorUser.get("login"));
                            Object dbId = authorUser.get("databaseId");
                            if (dbId != null) dto.setAuthorGithubId(((Number) dbId).longValue());
                        }
                    }
                    Map<String, Object> committer = (Map<String, Object>) node.get("committer");
                    if (committer != null) {
                        Map<String, Object> committerUser = (Map<String, Object>) committer.get("user");
                        if (committerUser != null) {
                            dto.setCommitterLogin((String) committerUser.get("login"));
                            Object dbId = committerUser.get("databaseId");
                            if (dbId != null) dto.setCommitterGithubId(((Number) dbId).longValue());
                        }
                    }
                    all.add(dto);
                }

                if (!hasNextPage) break;
            } catch (GitHubApiException e) {
                throw e;
            } catch (Exception e) {
                log.error("GraphQL commit fetch failed for {}/{}: {}", owner, repo, e.getMessage());
                break;
            }
        }
        return all;
    }

    // --- DTOs ---

    @Data
    public static class GraphQLCommitDto {
        private String sha;
        private int additions;
        private int deletions;
        private OffsetDateTime committedDate;
        private String message;
        private String authorLogin;
        private Long authorGithubId;
        private String authorEmail;
        private String committerLogin;
        private Long committerGithubId;
    }

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
        private AuthorDto committer;

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
        private AuthorDto committer;
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
    public static class GitHubPRDetailDto {
        private Integer number;
        private Integer additions;
        private Integer deletions;
        @JsonProperty("changed_files")
        private Integer changedFiles;
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

    @Data
    public static class GitHubReleaseDto {
        @JsonProperty("tag_name")
        private String tagName;
        private String name;
        @JsonProperty("published_at")
        private OffsetDateTime publishedAt;
        private Boolean draft;
        private Boolean prerelease;
    }

    @Data
    public static class GitHubRepoMetaDto {
        @JsonProperty("stargazers_count")
        private Integer stargazersCount;
        @JsonProperty("forks_count")
        private Integer forksCount;
        @JsonProperty("watchers_count")
        private Integer watchersCount;
        @JsonProperty("open_issues_count")
        private Integer openIssuesCount;
        private String language;
        private String description;
    }

    public GitHubRepoMetaDto getRepoMeta(String accessToken, UUID userId, String owner, String repo) {
        try {
            return webClientBuilder.build()
                .get()
                .uri("https://api.github.com/repos/{owner}/{repo}", owner, repo)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .bodyToMono(GitHubRepoMetaDto.class)
                .block();
        } catch (Exception e) {
            log.warn("Failed to fetch repo meta for {}/{}: {}", owner, repo, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public String forkRepo(String accessToken, UUID userId, String owner, String repo) {
        checkRateLimit(userId);
        try {
            Map<String, Object> result = webClientBuilder.build()
                .post()
                .uri(GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/forks")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .bodyValue(Map.of())
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Failed to fork {}/{}: {}", owner, repo, e.getStatusCode());
                    return Mono.error(new GitHubApiException("Fork failed: " + e.getStatusCode()));
                })
                .block();
            return result != null ? (String) result.get("html_url") : null;
        } catch (GitHubApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubApiException("Fork failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Long> getLanguages(String accessToken, UUID userId, String owner, String repo) {
        checkRateLimit(userId);
        try {
            Map<String, Long> result = (Map<String, Long>) webClientBuilder.build()
                .get()
                .uri(GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/languages")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .exchangeToMono(resp -> {
                    String remaining = resp.headers().asHttpHeaders().getFirst("X-RateLimit-Remaining");
                    if (remaining != null) {
                        redisTemplate.opsForValue().set("ga:ratelimit:gh:" + userId, remaining, 1, TimeUnit.HOURS);
                    }
                    return resp.bodyToMono(Map.class);
                })
                .block();
            return result != null ? result : Map.of();
        } catch (Exception e) {
            log.warn("Could not fetch languages for {}/{}: {}", owner, repo, e.getMessage());
            return Map.of();
        }
    }

    @Data
    public static class GitHubIssueDto {
        @JsonProperty("number")     private Integer number;
        @JsonProperty("title")      private String title;
        @JsonProperty("state")      private String state;
        @JsonProperty("created_at") private OffsetDateTime createdAt;
        @JsonProperty("closed_at")  private OffsetDateTime closedAt;
        @JsonProperty("user")       private GitHubPRDto.UserDto user;
        @JsonProperty("pull_request") private Object pullRequest;
    }

    public List<GitHubIssueDto> getIssues(String accessToken, UUID userId,
                                           String owner, String repo,
                                           OffsetDateTime since) {
        List<GitHubIssueDto> all = new ArrayList<>();
        int page = 1;
        String sinceParam = since != null ? "&since=" + since.toString() : "";
        while (true) {
            List<GitHubIssueDto> batch = get(
                accessToken, userId,
                "/repos/" + owner + "/" + repo + "/issues?state=all&per_page=" + PER_PAGE
                    + "&page=" + page + sinceParam,
                GitHubIssueDto.class
            );
            if (batch.isEmpty()) break;
            all.addAll(batch.stream().filter(i -> i.getPullRequest() == null).toList());
            if (batch.size() < PER_PAGE) break;
            page++;
        }
        return all;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    public List<GitHubReleaseDto> getReleases(String accessToken, UUID userId, String owner, String repo) {
        List<GitHubReleaseDto> all = new ArrayList<>();
        int page = 1;
        while (true) {
            try {
                List raw = webClientBuilder.build()
                    .get()
                    .uri("https://api.github.com/repos/{owner}/{repo}/releases?per_page=100&page={page}", owner, repo, page)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();
                if (raw == null || raw.isEmpty()) break;
                List<GitHubReleaseDto> page_results = ((List<?>) raw).stream()
                    .map(item -> objectMapper.convertValue(item, GitHubReleaseDto.class))
                    .filter(r -> !Boolean.TRUE.equals(r.getDraft()) && !Boolean.TRUE.equals(r.getPrerelease()))
                    .collect(java.util.stream.Collectors.toList());
                all.addAll(page_results);
                if (raw.size() < 100) break;
                page++;
            } catch (Exception e) {
                log.warn("Failed to fetch releases page {} for {}/{}: {}", page, owner, repo, e.getMessage());
                break;
            }
        }
        return all;
    }
}
