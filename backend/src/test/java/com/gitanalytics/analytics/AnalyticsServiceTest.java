package com.gitanalytics.analytics;

import com.gitanalytics.analytics.dao.AnalyticsDao;
import com.gitanalytics.analytics.dto.*;
import com.gitanalytics.analytics.service.AnalyticsService;
import com.gitanalytics.auth.dao.UserDao;
import com.gitanalytics.auth.entity.User;
import com.gitanalytics.ingestion.dao.CommitDao;
import com.gitanalytics.ingestion.dao.PullRequestDao;
import com.gitanalytics.ingestion.dao.ReleaseDao;
import com.gitanalytics.ingestion.dao.TrackedRepoDao;
import com.gitanalytics.ingestion.entity.PullRequest;
import com.gitanalytics.shared.cache.CacheRepository;
import com.gitanalytics.shared.client.GroqApiClient;
import com.gitanalytics.shared.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceTest {

    @Mock private AnalyticsDao analyticsDao;
    @Mock private UserDao userDao;
    @Mock private CommitDao commitDao;
    @Mock private PullRequestDao pullRequestDao;
    @Mock private ReleaseDao releaseDao;
    @Mock private TrackedRepoDao trackedRepoDao;
    @Mock private CacheRepository cacheRepository;
    @Mock private GroqApiClient groqApiClient;
    @Mock private AppProperties appProperties;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        // Make cacheRepository.cached() a pass-through: always call the loader (no caching in tests)
        when(cacheRepository.cached(anyString(), anyLong(), any()))
            .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());

        AppProperties.Redis redisCfg = new AppProperties.Redis();
        redisCfg.setAnalyticsCacheTtl(3600);
        redisCfg.setAiSummaryCacheTtl(21600);
        redisCfg.setFeedCacheTtl(900);
        redisCfg.setStreakCacheTtl(3600);
        redisCfg.setDashboardCacheTtl(300);
        when(appProperties.getRedis()).thenReturn(redisCfg);

        service = new AnalyticsService(analyticsDao, userDao, commitDao, pullRequestDao,
            releaseDao, trackedRepoDao, cacheRepository, groqApiClient, appProperties);
    }

    // ---------- PR Size Distribution ----------

    @Test
    void getPRSizeDistribution_bucketsCorrectly() {
        User user = user();
        when(userDao.findById(user.getId())).thenReturn(Optional.of(user));
        when(pullRequestDao.findByUserAndAuthorAndDateRange(any(), any(), any(), any())).thenReturn(List.of(
            pr(5),   // XS
            pr(9),   // XS
            pr(15),  // S
            pr(49),  // S
            pr(100), // M
            pr(300), // L
            pr(1500) // XL
        ));

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
        when(userDao.findById(user.getId())).thenReturn(Optional.of(user));
        when(pullRequestDao.findByUserAndAuthorAndDateRange(any(), any(), any(), any()))
            .thenReturn(Collections.emptyList());

        PRSizeDistributionDto result = service.getPRSizeDistribution(
            user.getId(), OffsetDateTime.now().minusDays(30), OffsetDateTime.now());

        result.getBuckets().values().forEach(v -> assertThat(v).isZero());
    }

    // ---------- Bus Factor ----------

    @Test
    void getBusFactor_singleContributor_returns100Percent() {
        UUID repoId = UUID.randomUUID();
        List<Object[]> rows = Collections.singletonList(new Object[]{"alice", 50L});
        when(analyticsDao.getBusFactorRows(any(), eq(repoId))).thenReturn(rows);

        BusFactorDto result = service.getBusFactor(UUID.randomUUID(), repoId);

        assertThat(result.getTopContributor()).isEqualTo("alice");
        assertThat(result.getTopContributorPercentage()).isEqualTo(100.0);
        assertThat(result.getTotalContributors()).isEqualTo(1);
    }

    @Test
    void getBusFactor_multipleContributors_calculatesPercentage() {
        UUID repoId = UUID.randomUUID();
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"alice", 60L});
        rows.add(new Object[]{"bob", 40L});
        when(analyticsDao.getBusFactorRows(any(), eq(repoId))).thenReturn(rows);

        BusFactorDto result = service.getBusFactor(UUID.randomUUID(), repoId);

        assertThat(result.getTopContributor()).isEqualTo("alice");
        assertThat(result.getTopContributorPercentage()).isEqualTo(60.0);
        assertThat(result.getTotalContributors()).isEqualTo(2);
    }

    @Test
    void getBusFactor_noCommits_returnsDefaults() {
        List<Object[]> empty = Collections.emptyList();
        when(analyticsDao.getBusFactorRows(any(), any())).thenReturn(empty);

        BusFactorDto result = service.getBusFactor(UUID.randomUUID(), UUID.randomUUID());

        assertThat(result.getTopContributor()).isNull();
        assertThat(result.getTopContributorPercentage()).isZero();
    }

    // ---------- Dashboard Cache ----------

    @Test
    void getDashboard_usesCacheRepositoryWithCorrectKey() {
        UUID userId = UUID.randomUUID();
        User user = user();
        when(userDao.findById(userId)).thenReturn(Optional.of(user));
        when(pullRequestDao.findByUserAndAuthorAndDateRange(any(), any(), any(), any())).thenReturn(List.of());
        when(analyticsDao.countCommits(any(), any(), any(), any())).thenReturn(0L);
        when(analyticsDao.getStreakData(any(), any(), any())).thenThrow(new RuntimeException("no data"));

        service.getDashboard(userId);

        verify(cacheRepository).cached(eq("ga:dashboard:" + userId), anyLong(), any());
    }

    // ---------- Stale PRs ----------

    @Test
    void getStalePRs_delegatesToDao() {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        when(pullRequestDao.findStalePRs(eq(userId), eq(repoId), any())).thenReturn(Collections.emptyList());

        List<PrSummaryDto> result = service.getStalePRs(userId, repoId, 7);

        assertThat(result).isEmpty();
        verify(pullRequestDao).findStalePRs(eq(userId), eq(repoId), any(OffsetDateTime.class));
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
}
