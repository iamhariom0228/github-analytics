package com.gitanalytics.analytics;

import com.gitanalytics.analytics.dto.*;
import com.gitanalytics.analytics.service.AnalyticsService;
import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.entity.UserPreferences;
import com.gitanalytics.auth.repository.UserPreferencesRepository;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.ingestion.entity.*;
import com.gitanalytics.ingestion.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnalyticsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("gitanalytics_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired private AnalyticsService analyticsService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPreferencesRepository userPreferencesRepository;
    @Autowired private TrackedRepoRepository trackedRepoRepository;
    @Autowired private CommitRepository commitRepository;
    @Autowired private PullRequestRepository pullRequestRepository;
    @Autowired private PrReviewRepository prReviewRepository;

    private static UUID userId;
    private static UUID repoId;


    @Test
    @Order(1)
    void setup_seedTestData() {
        User user = userRepository.save(User.builder()
            .githubId(88_888L)
            .username("test-user")
            .email("test@example.com")
            .avatarUrl("https://example.com/avatar.png")
            .accessTokenEncrypted("dGVzdA==") // base64 placeholder
            .build());
        userId = user.getId();

        userPreferencesRepository.save(UserPreferences.builder()
            .user(user).digestEnabled(true).digestDayOfWeek(1).digestHour(9).timezone("UTC")
            .build());

        TrackedRepo repo = trackedRepoRepository.save(TrackedRepo.builder()
            .user(user).owner("test-user").name("test-repo")
            .fullName("test-user/test-repo").githubRepoId(77_777L)
            .syncStatus(TrackedRepo.SyncStatus.DONE)
            .lastSyncedAt(OffsetDateTime.now())
            .build());
        repoId = repo.getId();

        // Seed commits across different hours/days for heatmap — use UTC to align with heatmap query
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC);
        commitRepository.saveAll(List.of(
            commit(repo, "test-user", base.minusDays(1).withHour(9), 10, 5),
            commit(repo, "test-user", base.minusDays(1).withHour(14), 20, 3),
            commit(repo, "test-user", base.minusDays(2).withHour(9), 15, 2),
            commit(repo, "test-user", base.minusDays(3).withHour(10), 8, 1),
            commit(repo, "alice", base.minusDays(1).withHour(11), 30, 10),
            commit(repo, "alice", base.minusDays(2).withHour(15), 5, 2)
        ));

        // Seed PRs
        PullRequest pr1 = pullRequestRepository.save(PullRequest.builder()
            .repo(repo).prNumber(1).title("Add feature X").authorLogin("test-user")
            .state(PullRequest.PrState.MERGED)
            .createdAt(base.minusDays(10))
            .firstReviewAt(base.minusDays(9))
            .mergedAt(base.minusDays(8))
            .additions(100).deletions(20).changedFiles(5)
            .build());

        pullRequestRepository.save(PullRequest.builder()
            .repo(repo).prNumber(2).title("Fix bug Y").authorLogin("test-user")
            .state(PullRequest.PrState.OPEN)
            .createdAt(base.minusDays(2))
            .additions(30).deletions(5).changedFiles(2)
            .build());

        prReviewRepository.save(PrReview.builder()
            .pullRequest(pr1).reviewerLogin("alice")
            .state(PrReview.ReviewState.APPROVED)
            .submittedAt(base.minusDays(9))
            .build());
    }

    @Test
    @Order(2)
    void getCommitHeatmap_returnsCorrectDayHourCounts() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(90);
        OffsetDateTime to = OffsetDateTime.now();
        List<HeatmapCellDto> heatmap = analyticsService.getCommitHeatmap(userId, null, "UTC", from, to);

        assertThat(heatmap).isNotEmpty();
        // Should have entries for the hours we seeded
        long hour9Count = heatmap.stream().filter(c -> c.getHour() == 9).mapToInt(HeatmapCellDto::getCount).sum();
        assertThat(hour9Count).isGreaterThanOrEqualTo(2); // two commits at hour 9
    }

    @Test
    @Order(3)
    void getCommitHeatmap_withRepoFilter_filtersCorrectly() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(90);
        OffsetDateTime to = OffsetDateTime.now();
        List<HeatmapCellDto> heatmap = analyticsService.getCommitHeatmap(userId, repoId.toString(), "UTC", from, to);
        assertThat(heatmap).isNotEmpty();
    }

    @Test
    @Order(4)
    void getPRLifecycle_calculatesMergeMetrics() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(30);
        OffsetDateTime to = OffsetDateTime.now();

        PRLifecycleDto lifecycle = analyticsService.getPRLifecycle(userId, from, to);

        assertThat(lifecycle.getMergedCount()).isEqualTo(1);
        assertThat(lifecycle.getTotalCount()).isEqualTo(2);
        assertThat(lifecycle.getAvgHoursToMerge()).isGreaterThan(0);
        assertThat(lifecycle.getAvgHoursToFirstReview()).isGreaterThan(0);
    }

    @Test
    @Order(5)
    void getTeamLeaderboard_ranksByCommitCount() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(30);
        OffsetDateTime to = OffsetDateTime.now();

        List<ContributorStatsDto> leaderboard = analyticsService.getTeamLeaderboard(userId, repoId, from, to);

        assertThat(leaderboard).hasSizeGreaterThanOrEqualTo(2);
        // test-user has 4 commits, alice has 2 — test-user should be first
        assertThat(leaderboard.get(0).getLogin()).isEqualTo("test-user");
        assertThat(leaderboard.get(0).getCommits()).isEqualTo(4L);
    }

    @Test
    @Order(6)
    void getBusFactor_calculatesConcentration() {
        BusFactorDto busFactor = analyticsService.getBusFactor(userId, repoId);

        assertThat(busFactor.getTopContributor()).isEqualTo("test-user");
        assertThat(busFactor.getTopContributorPercentage()).isGreaterThan(50.0);
        assertThat(busFactor.getTotalContributors()).isEqualTo(2);
    }

    @Test
    @Order(7)
    void getStreak_returnsNonZeroStreak() {
        StreakDto streak = analyticsService.getStreak(userId, "test-user", "UTC");

        // We have commits on consecutive days (days -3, -2, -1)
        assertThat(streak.getCurrentStreak()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @Order(8)
    void getStalePRs_returnsOpenPRsOlderThanThreshold() {
        List<PrSummaryDto> stalePRs = analyticsService.getStalePRs(userId, repoId, 1);

        // PR #2 is open and was created 2 days ago — should appear with threshold=1
        assertThat(stalePRs).anyMatch(pr -> pr.getPrNumber() == 2);
    }

    // ---------- Helpers ----------

    private Commit commit(TrackedRepo repo, String login, OffsetDateTime when, int add, int del) {
        return Commit.builder()
            .repo(repo)
            .sha(UUID.randomUUID().toString().replace("-", "") + "00000000")
            .authorLogin(login)
            .authorGithubId(login.equals("test-user") ? 88_888L : 11_111L)
            .messageSummary("test commit")
            .additions(add).deletions(del)
            .committedAt(when)
            .build();
    }
}
