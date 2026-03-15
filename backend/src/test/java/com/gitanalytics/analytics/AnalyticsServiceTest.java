package com.gitanalytics.analytics;

import com.gitanalytics.analytics.dto.*;
import com.gitanalytics.analytics.service.AnalyticsService;
import com.gitanalytics.auth.entity.User;
import com.gitanalytics.auth.repository.UserRepository;
import com.gitanalytics.ingestion.entity.PullRequest;
import com.gitanalytics.ingestion.entity.TrackedRepo;
import com.gitanalytics.ingestion.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.gitanalytics.shared.client.GroqApiClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceTest {

    @Mock private EntityManager em;
    @Mock private UserRepository userRepository;
    @Mock private CommitRepository commitRepository;
    @Mock private PullRequestRepository pullRequestRepository;
    @Mock private PrReviewRepository prReviewRepository;
    @Mock private TrackedRepoRepository trackedRepoRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private GroqApiClient groqApiClient;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new AnalyticsService(userRepository, commitRepository, pullRequestRepository,
            prReviewRepository, trackedRepoRepository, redisTemplate, groqApiClient);
        // inject EntityManager via reflection (it's @PersistenceContext)
        try {
            var field = AnalyticsService.class.getDeclaredField("em");
            field.setAccessible(true);
            field.set(service, em);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- PR Size Distribution ----------

    @Test
    void getPRSizeDistribution_bucketsCorrectly() {
        User user = user();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        List<PullRequest> prs = List.of(
            pr(5),   // XS
            pr(9),   // XS
            pr(15),  // S
            pr(49),  // S
            pr(100), // M
            pr(300), // L
            pr(1500) // XL
        );
        when(pullRequestRepository.findByUserAndAuthorAndDateRange(any(), any(), any(), any()))
            .thenReturn(prs);

        PRSizeDistributionDto result = service.getPRSizeDistribution(
            user.getId(), OffsetDateTime.now().minusDays(30), OffsetDateTime.now());

        assertThat(result.getBuckets().get("XS")).isEqualTo(2L);
        assertThat(result.getBuckets().get("S")).isEqualTo(2L);
        assertThat(result.getBuckets().get("M")).isEqualTo(1L);
        assertThat(result.getBuckets().get("L")).isEqualTo(1L);
        assertThat(result.getBuckets().get("XL")).isEqualTo(1L);
    }

    @Test
    void getPRSizeDistribution_emptyList_returnsZeroBuckets() {
        User user = user();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(pullRequestRepository.findByUserAndAuthorAndDateRange(any(), any(), any(), any()))
            .thenReturn(Collections.emptyList());

        PRSizeDistributionDto result = service.getPRSizeDistribution(
            user.getId(), OffsetDateTime.now().minusDays(30), OffsetDateTime.now());

        result.getBuckets().values().forEach(v -> assertThat(v).isZero());
    }

    // ---------- Bus Factor ----------

    @Test
    void getBusFactor_singleContributor_returns100Percent() {
        UUID repoId = UUID.randomUUID();
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"alice", 50L});
        var q = mockQuery(rows);
        when(em.createNativeQuery(anyString())).thenReturn(q);

        BusFactorDto result = service.getBusFactor(UUID.randomUUID(), repoId);

        assertThat(result.getTopContributor()).isEqualTo("alice");
        assertThat(result.getTopContributorPercentage()).isEqualTo(100.0);
        assertThat(result.getTotalContributors()).isEqualTo(1);
    }

    @Test
    void getBusFactor_multipleContributors_calculatesPercentage() {
        UUID repoId = UUID.randomUUID();
        List<Object[]> rows = List.of(
            new Object[]{"alice", 60L},
            new Object[]{"bob", 40L}
        );
        var q = mockQuery(rows);
        when(em.createNativeQuery(anyString())).thenReturn(q);

        BusFactorDto result = service.getBusFactor(UUID.randomUUID(), repoId);

        assertThat(result.getTopContributor()).isEqualTo("alice");
        assertThat(result.getTopContributorPercentage()).isEqualTo(60.0);
        assertThat(result.getTotalContributors()).isEqualTo(2);
    }

    @Test
    void getBusFactor_noCommits_returnsDefaults() {
        var q = mockQuery(Collections.emptyList());
        when(em.createNativeQuery(anyString())).thenReturn(q);

        BusFactorDto result = service.getBusFactor(UUID.randomUUID(), UUID.randomUUID());

        assertThat(result.getTopContributor()).isNull();
        assertThat(result.getTopContributorPercentage()).isZero();
    }

    // ---------- Dashboard Cache ----------

    @Test
    void getDashboard_returnsCachedValueWithoutHittingDb() {
        UUID userId = UUID.randomUUID();
        DashboardSummaryDto cached = DashboardSummaryDto.builder()
            .weeklyCommits(42L).currentStreak(7).build();
        when(valueOps.get("ga:dashboard:" + userId)).thenReturn(cached);

        DashboardSummaryDto result = service.getDashboard(userId);

        assertThat(result.getWeeklyCommits()).isEqualTo(42L);
        assertThat(result.getCurrentStreak()).isEqualTo(7);
        verifyNoInteractions(userRepository);
    }

    // ---------- Stale PRs ----------

    @Test
    void getStalePRs_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        when(pullRequestRepository.findStalePRs(eq(repoId), any())).thenReturn(Collections.emptyList());

        List<PrSummaryDto> result = service.getStalePRs(userId, repoId, 7);

        assertThat(result).isEmpty();
        verify(pullRequestRepository).findStalePRs(eq(repoId), any(OffsetDateTime.class));
    }

    // ---------- Helpers ----------

    private User user() {
        return User.builder()
            .id(UUID.randomUUID())
            .githubId(1L)
            .username("alice")
            .accessTokenEncrypted("enc")
            .build();
    }

    private PullRequest pr(int changedFiles) {
        return PullRequest.builder()
            .prNumber(1)
            .title("test pr")
            .authorLogin("alice")
            .state(PullRequest.PrState.MERGED)
            .changedFiles(changedFiles)
            .createdAt(OffsetDateTime.now().minusDays(1))
            .build();
    }

    @SuppressWarnings("unchecked")
    private jakarta.persistence.Query mockQuery(List<?> results) {
        // Use RETURNS_SELF so setParameter(name, value) returns the query without explicit stubbing
        jakarta.persistence.Query q = mock(jakarta.persistence.Query.class, Mockito.RETURNS_SELF);
        when(q.getResultList()).thenReturn((List<Object>) results);
        return q;
    }
}
