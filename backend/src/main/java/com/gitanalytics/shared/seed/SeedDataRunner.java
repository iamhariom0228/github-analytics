package com.gitanalytics.shared.seed;

import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.entity.UserPreferences;
import com.gitanalytics.auth.repository.UserPreferencesRepository;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.ingestion.entity.*;
import com.gitanalytics.ingestion.repository.*;
import com.gitanalytics.shared.util.EncryptionUtil;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class SeedDataRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final TrackedRepoRepository trackedRepoRepository;
    private final CommitRepository commitRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PrReviewRepository prReviewRepository;
    private final EncryptionUtil encryptionUtil;
    private final EntityManager em;

    private static final long DEMO_GITHUB_ID = 999_999L;

    @Override
    @Transactional
    public void run(String... args) {
        boolean fullySeeded = userRepository.findByGithubId(DEMO_GITHUB_ID)
            .map(u -> {
                List<com.gitanalytics.ingestion.entity.TrackedRepo> repos =
                    trackedRepoRepository.findByUserId(u.getId());
                if (repos.isEmpty()) return false;
                long commitCount = commitRepository.countByRepoId(repos.get(0).getId());
                return commitCount > 0;
            }).orElse(false);

        if (fullySeeded) {
            log.info("Demo seed data already exists — skipping.");
            return;
        }

        // Partial seed detected — delete existing partial data so we start fresh
        userRepository.findByGithubId(DEMO_GITHUB_ID).ifPresent(u -> {
            log.info("Partial seed detected — cleaning up before re-seeding...");
            trackedRepoRepository.findByUserId(u.getId())
                .forEach(r -> {
                    prReviewRepository.deleteByPullRequestRepoId(r.getId());
                    pullRequestRepository.deleteByRepoId(r.getId());
                    commitRepository.deleteByRepoId(r.getId());
                    trackedRepoRepository.delete(r);
                });
            userPreferencesRepository.deleteByUserId(u.getId());
            userRepository.delete(u);
        });
        em.flush();
        em.clear();

        log.info("Seeding demo data for local development...");

        User user = userRepository.save(User.builder()
            .githubId(DEMO_GITHUB_ID)
            .username("demo-user")
            .email("demo@example.com")
            .avatarUrl("https://avatars.githubusercontent.com/u/583231")
            .accessTokenEncrypted(encryptionUtil.encrypt("demo-token-not-real-do-not-use"))
            .build());

        userPreferencesRepository.save(UserPreferences.builder()
            .user(user)
            .digestEnabled(true)
            .digestDayOfWeek(1)
            .digestHour(9)
            .timezone("UTC")
            .build());

        TrackedRepo repo1 = trackedRepoRepository.save(TrackedRepo.builder()
            .user(user).owner("demo-user").name("spring-petclinic")
            .fullName("demo-user/spring-petclinic").githubRepoId(100_001L)
            .isPrivate(false).syncStatus(TrackedRepo.SyncStatus.DONE)
            .lastSyncedAt(OffsetDateTime.now()).build());

        TrackedRepo repo2 = trackedRepoRepository.save(TrackedRepo.builder()
            .user(user).owner("demo-user").name("my-portfolio")
            .fullName("demo-user/my-portfolio").githubRepoId(100_002L)
            .isPrivate(true).syncStatus(TrackedRepo.SyncStatus.DONE)
            .lastSyncedAt(OffsetDateTime.now()).build());

        seedCommits(user, repo1, repo2);
        List<PullRequest> prs = seedPullRequests(repo1, repo2);
        seedReviews(prs);

        log.info("Demo seed complete. Log in at http://localhost:8080/api/v1/dev/demo-login");
    }

    private void seedCommits(User user, TrackedRepo repo1, TrackedRepo repo2) {
        Random rng = new Random(42);
        OffsetDateTime now = OffsetDateTime.now();
        List<Commit> commits = new ArrayList<>();

        for (int day = 90; day >= 0; day--) {
            if (day > 0 && rng.nextInt(10) < 3) continue; // ~30% days off for realistic streaks
            OffsetDateTime base = now.minusDays(day);
            int perDay = 1 + rng.nextInt(5);
            for (int c = 0; c < perDay; c++) {
                TrackedRepo repo = rng.nextBoolean() ? repo1 : repo2;
                commits.add(Commit.builder()
                    .repo(repo)
                    .sha(UUID.randomUUID().toString().replace("-", "") + "00000000")
                    .authorLogin("demo-user")
                    .authorGithubId(DEMO_GITHUB_ID)
                    .messageSummary(pickRandom(COMMIT_MESSAGES, rng))
                    .additions(10 + rng.nextInt(200))
                    .deletions(rng.nextInt(80))
                    .committedAt(base.withHour(7 + rng.nextInt(14)).withMinute(rng.nextInt(60)))
                    .build());
            }
            // Add a few commits by teammates for leaderboard
            if (rng.nextInt(3) == 0) {
                String teammate = pickRandom(TEAMMATES, rng);
                commits.add(Commit.builder()
                    .repo(repo1)
                    .sha(UUID.randomUUID().toString().replace("-", "") + "00000000")
                    .authorLogin(teammate)
                    .authorGithubId((long) teammate.hashCode())
                    .messageSummary(pickRandom(COMMIT_MESSAGES, rng))
                    .additions(5 + rng.nextInt(100))
                    .deletions(rng.nextInt(30))
                    .committedAt(base.withHour(9 + rng.nextInt(8)).withMinute(rng.nextInt(60)))
                    .build());
            }
        }

        commitRepository.saveAll(commits);
        log.info("  → {} commits seeded", commits.size());
    }

    private List<PullRequest> seedPullRequests(TrackedRepo repo1, TrackedRepo repo2) {
        Random rng = new Random(42);
        OffsetDateTime now = OffsetDateTime.now();
        List<PullRequest> prs = new ArrayList<>();

        TrackedRepo[] repos = {repo1, repo2};
        int[] fileCounts = {2, 8, 15, 60, 300, 1200};

        for (int i = 1; i <= 30; i++) {
            int daysAgo = rng.nextInt(90);
            OffsetDateTime created = now.minusDays(daysAgo).withHour(10).withMinute(rng.nextInt(60));
            OffsetDateTime firstReview = created.plusHours(1 + rng.nextInt(24));
            boolean isMerged = rng.nextInt(10) > 2;
            boolean isOpen = !isMerged && daysAgo < 14;

            PullRequest.PrState state = isMerged ? PullRequest.PrState.MERGED
                : (isOpen ? PullRequest.PrState.OPEN : PullRequest.PrState.CLOSED);
            OffsetDateTime mergedAt = isMerged ? firstReview.plusHours(1 + rng.nextInt(48)) : null;

            prs.add(PullRequest.builder()
                .repo(repos[i % 2])
                .prNumber(i)
                .title(pickRandom(PR_TITLES, rng))
                .authorLogin("demo-user")
                .state(state)
                .createdAt(created)
                .firstReviewAt(firstReview)
                .mergedAt(mergedAt)
                .closedAt(mergedAt != null ? mergedAt : (isOpen ? null : firstReview.plusHours(2)))
                .additions(20 + rng.nextInt(300))
                .deletions(rng.nextInt(150))
                .changedFiles(fileCounts[rng.nextInt(fileCounts.length)])
                .build());
        }

        pullRequestRepository.saveAll(prs);
        log.info("  → {} pull requests seeded", prs.size());
        return prs;
    }

    private void seedReviews(List<PullRequest> prs) {
        Random rng = new Random(42);
        List<PrReview> reviews = new ArrayList<>();

        for (PullRequest pr : prs) {
            if (pr.getFirstReviewAt() == null) continue;
            reviews.add(PrReview.builder()
                .pullRequest(pr)
                .reviewerLogin(pickRandom(TEAMMATES, rng))
                .state(rng.nextBoolean() ? PrReview.ReviewState.APPROVED : PrReview.ReviewState.CHANGES_REQUESTED)
                .submittedAt(pr.getFirstReviewAt())
                .build());
        }

        prReviewRepository.saveAll(reviews);
        log.info("  → {} reviews seeded", reviews.size());
    }

    private static <T> T pickRandom(T[] arr, Random rng) {
        return arr[rng.nextInt(arr.length)];
    }

    private static final String[] COMMIT_MESSAGES = {
        "fix: resolve null pointer exception in analytics service",
        "feat: add contribution heatmap endpoint",
        "refactor: extract GitHub API client into separate module",
        "test: add unit tests for PR lifecycle calculation",
        "docs: update API documentation",
        "chore: bump Spring Boot to 3.2.3",
        "style: apply consistent code formatting",
        "perf: add Redis cache for dashboard queries",
        "fix: handle edge case in streak calculation",
        "feat: implement weekly email digest scheduler",
        "refactor: move Kafka event classes to shared module",
        "fix: correct timezone handling in heatmap query",
        "feat: add bus factor analysis endpoint",
        "test: integration tests with Testcontainers",
        "chore: add Flyway migration for digest_logs table"
    };

    private static final String[] PR_TITLES = {
        "Add contribution heatmap feature",
        "Implement GitHub OAuth flow",
        "Optimize dashboard query with Redis cache",
        "Add team leaderboard endpoint",
        "Fix PR review timing calculation",
        "Implement weekly email digest",
        "Add rate limiting middleware",
        "Improve error handling across services",
        "Add cursor-based pagination for commits",
        "Implement webhook signature validation",
        "Add Testcontainers integration tests",
        "Refactor analytics service queries",
        "Add bus factor detection",
        "Implement sync status polling",
        "Add contribution streak detection"
    };

    private static final String[] TEAMMATES = {"alice", "bob", "carol", "dave"};
}
