# Tier B Implementation Plan

Latest Flyway migration: **V5__repo_metadata.sql** — next migration is **V6**.

Features ordered by implementation difficulty (easiest first):

1. [Feature 14 — Contributor Network Graph](#feature-14--contributor-network-graph) — frontend-only
2. [Feature 9 — Stars & Forks Growth](#feature-9--stars--forks-growth) — backend + frontend
3. [Feature 8 — Release Trend Chart](#feature-8--release-trend-chart) — backend + frontend
4. [Feature 7 — Issues Sync & Analytics](#feature-7--issues-sync--analytics) — backend + frontend
5. [Feature 12 — Language Bytes Upgrade](#feature-12--language-bytes-upgrade) — backend + frontend
6. [Feature 11 — Compare Two Repos](#feature-11--compare-two-repos) — backend + frontend
7. [Feature 10 — Compare Two Contributors](#feature-10--compare-two-contributors) — backend + frontend
8. [Feature 13 — Public Shareable Snapshots](#feature-13--public-shareable-snapshots) — backend + frontend

---

## Feature 14 — Contributor Network Graph

**Type: Frontend-only**

### What it does
Replaces the collaboration bar charts on the `/analytics` page with an interactive force-directed graph that makes reviewer relationships immediately intuitive. Nodes represent contributors; edges are weighted by how many PRs each pair has reviewed together, so cluster patterns and key reviewers are visible at a glance.

### Files to create
- `frontend/src/components/charts/ContributorNetworkGraph.tsx`

### Files to modify
- `frontend/src/app/(app)/analytics/page.tsx` — swap the collaboration section
- `package.json` — add `react-force-graph-2d`

### Step-by-step implementation

#### Step 1 — Install the library

```bash
cd frontend && npm install react-force-graph-2d
```

`react-force-graph-2d` wraps `d3-force` into a React component and ships its own types.

#### Step 2 — Add types to `types/index.ts`

No new API types needed — `CollaborationData` (already defined) provides the edge data:

```ts
// Already exists — used as-is:
export interface CollaboratorEntry { login: string; count: number; }
export interface CollaborationData {
  topReviewersOfMe: CollaboratorEntry[];
  topPeopleIReview: CollaboratorEntry[];
}
```

#### Step 3 — Build the component

```tsx
// frontend/src/components/charts/ContributorNetworkGraph.tsx
"use client";

import { useRef, useEffect, useCallback } from "react";
import dynamic from "next/dynamic";
import type { CollaborationData } from "@/types";

// react-force-graph-2d uses browser APIs — must be dynamically imported
const ForceGraph2D = dynamic(() => import("react-force-graph-2d"), { ssr: false });

interface Node { id: string; val: number; color: string; isSelf: boolean; }
interface Link { source: string; target: string; value: number; }

interface Props {
  data: CollaborationData;
  selfLogin: string;
}

function buildGraph(data: CollaborationData, selfLogin: string) {
  const nodeMap = new Map<string, Node>();
  const linkMap = new Map<string, Link>();

  const ensureNode = (login: string) => {
    if (!nodeMap.has(login)) {
      nodeMap.set(login, {
        id: login,
        val: 1,
        color: login === selfLogin ? "hsl(var(--primary))" : "#64748b",
        isSelf: login === selfLogin,
      });
    }
  };

  ensureNode(selfLogin);

  for (const entry of data.topReviewersOfMe) {
    ensureNode(entry.login);
    const key = [entry.login, selfLogin].sort().join("__");
    const existing = linkMap.get(key);
    linkMap.set(key, {
      source: entry.login,
      target: selfLogin,
      value: (existing?.value ?? 0) + entry.count,
    });
    // Increase node size proportional to interaction count
    const node = nodeMap.get(entry.login)!;
    node.val = Math.max(node.val, Math.sqrt(entry.count) * 2);
  }

  for (const entry of data.topPeopleIReview) {
    ensureNode(entry.login);
    const key = [selfLogin, entry.login].sort().join("__");
    const existing = linkMap.get(key);
    linkMap.set(key, {
      source: selfLogin,
      target: entry.login,
      value: (existing?.value ?? 0) + entry.count,
    });
    const node = nodeMap.get(entry.login)!;
    node.val = Math.max(node.val, Math.sqrt(entry.count) * 2);
  }

  return {
    nodes: Array.from(nodeMap.values()),
    links: Array.from(linkMap.values()),
  };
}

export function ContributorNetworkGraph({ data, selfLogin }: Props) {
  const graphData = buildGraph(data, selfLogin);
  const fgRef = useRef<{ centerAt: (x: number, y: number, ms: number) => void } | null>(null);

  useEffect(() => {
    // Auto-center after mount
    setTimeout(() => fgRef.current?.centerAt(0, 0, 400), 300);
  }, []);

  const nodeLabel = useCallback((node: Node) =>
    `<div style="background:#1e293b;color:#f1f5f9;padding:4px 8px;border-radius:6px;font-size:12px">${node.id}</div>`,
    []
  );

  const linkColor = useCallback(
    (link: Link) => `rgba(100,116,139,${Math.min(0.8, 0.15 + link.value * 0.05)})`,
    []
  );

  const linkWidth = useCallback((link: Link) => Math.min(6, 1 + link.value * 0.4), []);

  if (graphData.nodes.length <= 1) {
    return (
      <div className="h-64 flex items-center justify-center text-muted-foreground text-sm">
        Not enough collaboration data to render graph.
      </div>
    );
  }

  return (
    <div className="w-full rounded-xl overflow-hidden border border-border bg-card" style={{ height: 400 }}>
      <ForceGraph2D
        ref={fgRef}
        graphData={graphData}
        nodeLabel={nodeLabel}
        nodeVal="val"
        nodeColor="color"
        linkColor={linkColor}
        linkWidth={linkWidth}
        linkDirectionalArrowLength={4}
        linkDirectionalArrowRelPos={1}
        backgroundColor="transparent"
        nodeCanvasObjectMode={() => "after"}
        nodeCanvasObject={(node, ctx, globalScale) => {
          const label = (node as Node).id;
          const fontSize = Math.max(8, 12 / globalScale);
          ctx.font = `${fontSize}px sans-serif`;
          ctx.fillStyle = (node as Node).isSelf
            ? "hsl(var(--primary))"
            : "hsl(var(--muted-foreground))";
          ctx.textAlign = "center";
          ctx.textBaseline = "top";
          ctx.fillText(label, node.x ?? 0, (node.y ?? 0) + 8);
        }}
        cooldownTicks={100}
        d3AlphaDecay={0.02}
        d3VelocityDecay={0.3}
      />
    </div>
  );
}
```

#### Step 4 — Wire into analytics page

In `frontend/src/app/(app)/analytics/page.tsx`, inside the collaboration section, replace the bar chart render with:

```tsx
import { ContributorNetworkGraph } from "@/components/charts/ContributorNetworkGraph";

// ...inside the collaboration card:
{collaborationData && user && (
  <ContributorNetworkGraph
    data={collaborationData}
    selfLogin={user.username}
  />
)}
```

Keep the existing `useCollaboration` hook call; no new API calls are needed.

---

## Feature 9 — Stars & Forks Growth

**Type: Backend + Frontend**

### What it does
Records daily snapshots of a repo's star and fork counts so users can see how their projects' popularity grows over time. A line chart on the repos/analytics page shows the trend, revealing spikes after releases or blog posts.

### Files to create
- `backend/src/main/resources/db/migration/V6__repo_stats_snapshots.sql`
- `backend/src/main/java/com/gitanalytics/ingestion/entity/RepoStatsSnapshot.java`
- `backend/src/main/java/com/gitanalytics/ingestion/repository/RepoStatsSnapshotRepository.java`
- `backend/src/main/java/com/gitanalytics/ingestion/scheduler/RepoStatsSnapshotScheduler.java`
- `backend/src/main/java/com/gitanalytics/analytics/dto/StarsForksSnapshotDto.java`

### Files to modify
- `backend/src/main/java/com/gitanalytics/analytics/controller/AnalyticsController.java` — add endpoint
- `backend/src/main/java/com/gitanalytics/analytics/service/AnalyticsService.java` — add service method
- `backend/src/main/java/com/gitanalytics/analytics/dao/AnalyticsDao.java` — add DAO method
- `backend/src/main/java/com/gitanalytics/analytics/dao/impl/AnalyticsDaoImpl.java` — implement
- `backend/src/main/java/com/gitanalytics/analytics/repository/AnalyticsRepository.java` — add query
- `frontend/src/types/index.ts` — add type
- `frontend/src/lib/api/client.ts` — add API function
- `frontend/src/hooks/useAnalytics.ts` — add hook
- `frontend/src/app/(app)/analytics/page.tsx` — add chart section

### Step-by-step implementation

#### Step 1 — Flyway migration

```sql
-- V6__repo_stats_snapshots.sql
CREATE TABLE IF NOT EXISTS repo_stats_snapshots (
    id          BIGSERIAL PRIMARY KEY,
    repo_id     UUID NOT NULL REFERENCES tracked_repos(id) ON DELETE CASCADE,
    snapshotted_on DATE NOT NULL,
    stars       INT NOT NULL DEFAULT 0,
    forks       INT NOT NULL DEFAULT 0,
    watchers    INT NOT NULL DEFAULT 0,
    UNIQUE (repo_id, snapshotted_on)
);

CREATE INDEX IF NOT EXISTS idx_rss_repo_date ON repo_stats_snapshots(repo_id, snapshotted_on DESC);
```

#### Step 2 — JPA Entity

```java
// RepoStatsSnapshot.java
package com.gitanalytics.ingestion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "repo_stats_snapshots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RepoStatsSnapshot {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private TrackedRepo repo;

    @Column(name = "snapshotted_on", nullable = false)
    private LocalDate snapshottedOn;

    private int stars;
    private int forks;
    private int watchers;
}
```

#### Step 3 — Repository

```java
// RepoStatsSnapshotRepository.java
package com.gitanalytics.ingestion.repository;

import com.gitanalytics.ingestion.entity.RepoStatsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RepoStatsSnapshotRepository extends JpaRepository<RepoStatsSnapshot, Long> {

    @Query(value = """
        INSERT INTO repo_stats_snapshots (repo_id, snapshotted_on, stars, forks, watchers)
        VALUES (:repoId, :snappedOn, :stars, :forks, :watchers)
        ON CONFLICT (repo_id, snapshotted_on) DO UPDATE
          SET stars = EXCLUDED.stars, forks = EXCLUDED.forks, watchers = EXCLUDED.watchers
        """, nativeQuery = true)
    @Modifying
    @Transactional
    void upsert(UUID repoId, LocalDate snappedOn, int stars, int forks, int watchers);

    @Query(value = """
        SELECT snapshotted_on, stars, forks, watchers
        FROM repo_stats_snapshots
        WHERE repo_id = :repoId
          AND snapshotted_on >= :since
        ORDER BY snapshotted_on ASC
        """, nativeQuery = true)
    List<Object[]> findSnapshotsSince(UUID repoId, LocalDate since);
}
```

#### Step 4 — Scheduled cron job

```java
// RepoStatsSnapshotScheduler.java
package com.gitanalytics.ingestion.scheduler;

import com.gitanalytics.ingestion.entity.TrackedRepo;
import com.gitanalytics.ingestion.repository.RepoStatsSnapshotRepository;
import com.gitanalytics.ingestion.repository.TrackedRepoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RepoStatsSnapshotScheduler {

    private final TrackedRepoRepository trackedRepoRepository;
    private final RepoStatsSnapshotRepository snapshotRepository;

    // Runs every day at 01:00 UTC
    @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
    public void takeSnapshots() {
        LocalDate today = LocalDate.now();
        List<TrackedRepo> repos = trackedRepoRepository.findAll();
        log.info("Taking stats snapshots for {} repos", repos.size());
        for (TrackedRepo repo : repos) {
            try {
                snapshotRepository.upsert(
                    repo.getId(), today,
                    repo.getStars()    != null ? repo.getStars()    : 0,
                    repo.getForks()    != null ? repo.getForks()    : 0,
                    repo.getWatchers() != null ? repo.getWatchers() : 0
                );
            } catch (Exception e) {
                log.warn("Failed to snapshot repo {}: {}", repo.getFullName(), e.getMessage());
            }
        }
    }
}
```

Ensure `@EnableScheduling` is on the main application class (or a `@Configuration` class).

#### Step 5 — DTO

```java
// StarsForksSnapshotDto.java
package com.gitanalytics.analytics.dto;

public record StarsForksSnapshotDto(String date, int stars, int forks, int watchers) {}
```

#### Step 6 — AnalyticsRepository query

Add to `AnalyticsRepository.java`:

```java
@Query(value = """
    SELECT snapshotted_on::text, stars, forks, watchers
    FROM repo_stats_snapshots
    WHERE repo_id = :repoId
      AND snapshotted_on >= CURRENT_DATE - INTERVAL '90 days'
    ORDER BY snapshotted_on ASC
    """, nativeQuery = true)
List<Object[]> getStarsForksTrend(UUID repoId);
```

#### Step 7 — AnalyticsDao interface + impl

In `AnalyticsDao.java` add:

```java
List<StarsForksSnapshotDto> getStarsForksTrend(UUID repoId);
```

In `AnalyticsDaoImpl.java` add:

```java
@Override
public List<StarsForksSnapshotDto> getStarsForksTrend(UUID repoId) {
    return analyticsRepository.getStarsForksTrend(repoId).stream()
        .map(r -> new StarsForksSnapshotDto(
            (String) r[0],
            ((Number) r[1]).intValue(),
            ((Number) r[2]).intValue(),
            ((Number) r[3]).intValue()))
        .toList();
}
```

#### Step 8 — AnalyticsService method

```java
public List<StarsForksSnapshotDto> getStarsForksTrend(UUID userId, UUID repoId) {
    trackedRepoDao.findById(repoId)
        .filter(r -> r.getUser().getId().equals(userId))
        .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
    return analyticsDao.getStarsForksTrend(repoId);
}
```

#### Step 9 — AnalyticsController endpoint

```java
@GetMapping("/repos/{repoId}/stars-forks-trend")
public ResponseEntity<ApiResponse<List<StarsForksSnapshotDto>>> getStarsForksTrend(
        @AuthenticationPrincipal UserDetails principal,
        @PathVariable UUID repoId) {
    UUID userId = UUID.fromString(principal.getUsername());
    return ResponseEntity.ok(ApiResponse.ok(analyticsService.getStarsForksTrend(userId, repoId)));
}
```

#### Step 10 — Frontend type

In `frontend/src/types/index.ts` add:

```ts
export interface StarsForksSnapshot {
  date: string;
  stars: number;
  forks: number;
  watchers: number;
}
```

#### Step 11 — Frontend API function

In `frontend/src/lib/api/client.ts` add:

```ts
import type { StarsForksSnapshot } from "@/types";

export const getStarsForksTrend = (repoId: string) =>
  apiClient
    .get<ApiResponse<StarsForksSnapshot[]>>(`/analytics/repos/${repoId}/stars-forks-trend`)
    .then(unwrap);
```

#### Step 12 — React Query hook

In `frontend/src/hooks/useAnalytics.ts` add:

```ts
import { getStarsForksTrend } from "@/lib/api/client";

export function useStarsForksTrend(repoId: string | undefined) {
  return useQuery({
    queryKey: ["stars-forks-trend", repoId],
    queryFn: () => getStarsForksTrend(repoId!),
    staleTime: ANALYTICS_STALE_MS,
    enabled: !!repoId,
  });
}
```

#### Step 13 — Chart component (inline in analytics page)

Add a new section in `frontend/src/app/(app)/analytics/page.tsx`:

```tsx
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts";
import { useStarsForksTrend } from "@/hooks/useAnalytics";

// Inside component, after existing repo selector state:
const { data: sfTrend, isLoading: sfLoading } = useStarsForksTrend(selectedRepoId);

// JSX section:
<div className="bg-card border border-border rounded-xl p-6">
  <h2 className="font-semibold mb-5">Stars & Forks Growth (90 days)</h2>
  {sfLoading ? (
    <div className="h-52 animate-pulse bg-muted rounded-xl" />
  ) : !sfTrend?.length ? (
    <div className="h-52 flex items-center justify-center text-muted-foreground text-sm">
      No snapshot data yet — check back tomorrow after the daily cron runs.
    </div>
  ) : (
    <ResponsiveContainer width="100%" height={208}>
      <LineChart data={sfTrend} margin={{ top: 4, right: 8, left: -20, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
        <XAxis dataKey="date" tick={{ fontSize: 10 }} tickLine={false} axisLine={false}
          tickFormatter={(v) => v.slice(5)} interval="preserveStartEnd" />
        <YAxis tick={{ fontSize: 10 }} tickLine={false} axisLine={false} allowDecimals={false} />
        <Tooltip
          contentStyle={{ background: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: 8, fontSize: 12 }}
        />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        <Line type="monotone" dataKey="stars" stroke="#f59e0b" strokeWidth={2} dot={false} name="Stars" />
        <Line type="monotone" dataKey="forks" stroke="hsl(var(--primary))" strokeWidth={2} dot={false} name="Forks" />
      </LineChart>
    </ResponsiveContainer>
  )}
</div>
```

---

## Feature 8 — Release Trend Chart

**Type: Backend + Frontend**

### What it does
Shows a monthly bar chart of how frequently a repository ships releases, helping teams spot release cadence patterns and gaps. The chart appears on the Explore / repo detail page alongside the existing health score.

### Files to create
- `backend/src/main/java/com/gitanalytics/analytics/dto/ReleaseTrendDto.java`

### Files to modify
- `backend/src/main/java/com/gitanalytics/ingestion/client/GitHubApiClient.java` — add `getReleases()`
- `backend/src/main/java/com/gitanalytics/ingestion/service/RepoService.java` — call `getReleases()` during sync
- `backend/src/main/java/com/gitanalytics/analytics/controller/AnalyticsController.java` — add endpoint
- `backend/src/main/java/com/gitanalytics/analytics/service/AnalyticsService.java` — add service method
- `backend/src/main/java/com/gitanalytics/analytics/dao/AnalyticsDao.java` — add DAO method
- `backend/src/main/java/com/gitanalytics/analytics/dao/impl/AnalyticsDaoImpl.java` — implement
- `backend/src/main/java/com/gitanalytics/analytics/repository/AnalyticsRepository.java` — add query
- `frontend/src/types/index.ts` — add type
- `frontend/src/lib/api/client.ts` — add API function
- `frontend/src/hooks/useAnalytics.ts` — add hook
- `frontend/src/app/(app)/explore/page.tsx` — add chart section

### Step-by-step implementation

#### Step 1 — No new migration needed

The `releases` table already exists (V5). The `Release` entity is already defined.

#### Step 2 — Add `getReleases` to `GitHubApiClient`

In `GitHubApiClient.java`, add a new DTO class and method:

```java
@Data
public static class GitHubReleaseDto {
    @JsonProperty("tag_name")  private String tagName;
    @JsonProperty("name")      private String name;
    @JsonProperty("published_at") private OffsetDateTime publishedAt;
    @JsonProperty("draft")     private boolean draft;
    @JsonProperty("prerelease") private boolean prerelease;
}

public List<GitHubReleaseDto> getReleases(String accessToken, UUID userId,
                                           String owner, String repo) {
    List<GitHubReleaseDto> all = new ArrayList<>();
    int page = 1;
    while (true) {
        List<GitHubReleaseDto> batch = get(
            accessToken, userId,
            "/repos/" + owner + "/" + repo + "/releases?per_page=" + PER_PAGE + "&page=" + page,
            GitHubReleaseDto.class
        );
        if (batch.isEmpty()) break;
        all.addAll(batch.stream().filter(r -> !r.isDraft()).toList());
        if (batch.size() < PER_PAGE) break;
        page++;
    }
    return all;
}
```

#### Step 3 — Call `getReleases` during sync in `RepoService`

In the sync flow within `RepoService` (or the Kafka consumer that performs the actual sync), after saving PRs, add:

```java
// After PR sync block:
List<GitHubApiClient.GitHubReleaseDto> ghReleases =
    gitHubApiClient.getReleases(accessToken, userId, repo.getOwner(), repo.getName());
for (GitHubApiClient.GitHubReleaseDto gr : ghReleases) {
    Release release = Release.builder()
        .repo(trackedRepo)
        .tagName(gr.getTagName())
        .name(gr.getName())
        .publishedAt(gr.getPublishedAt())
        .build();
    releaseDao.upsert(release);
}
log.info("Synced {} releases for {}", ghReleases.size(), repo.getFullName());
```

#### Step 4 — DTO

```java
// ReleaseTrendDto.java
package com.gitanalytics.analytics.dto;

public record ReleaseTrendDto(String month, long count) {}
```

#### Step 5 — AnalyticsRepository query

```java
@Query(value = """
    SELECT TO_CHAR(published_at AT TIME ZONE 'UTC', 'YYYY-MM') AS month,
           COUNT(*) AS release_count
    FROM releases
    WHERE repo_id = :repoId
      AND published_at IS NOT NULL
      AND published_at >= NOW() - INTERVAL '12 months'
    GROUP BY month
    ORDER BY month ASC
    """, nativeQuery = true)
List<Object[]> getReleaseTrend(UUID repoId);
```

#### Step 6 — AnalyticsDao

In `AnalyticsDao.java` add:

```java
List<ReleaseTrendDto> getReleaseTrend(UUID repoId);
```

In `AnalyticsDaoImpl.java` add:

```java
@Override
public List<ReleaseTrendDto> getReleaseTrend(UUID repoId) {
    return analyticsRepository.getReleaseTrend(repoId).stream()
        .map(r -> new ReleaseTrendDto((String) r[0], ((Number) r[1]).longValue()))
        .toList();
}
```

#### Step 7 — AnalyticsService

```java
public List<ReleaseTrendDto> getReleaseTrend(UUID userId, UUID repoId) {
    trackedRepoDao.findById(repoId)
        .filter(r -> r.getUser().getId().equals(userId))
        .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
    return analyticsDao.getReleaseTrend(repoId);
}
```

#### Step 8 — AnalyticsController

```java
@GetMapping("/repos/{repoId}/release-trend")
public ResponseEntity<ApiResponse<List<ReleaseTrendDto>>> getReleaseTrend(
        @AuthenticationPrincipal UserDetails principal,
        @PathVariable UUID repoId) {
    UUID userId = UUID.fromString(principal.getUsername());
    return ResponseEntity.ok(ApiResponse.ok(analyticsService.getReleaseTrend(userId, repoId)));
}
```

#### Step 9 — Frontend type

```ts
// In types/index.ts
export interface ReleaseTrendPoint {
  month: string;  // "YYYY-MM"
  count: number;
}
```

#### Step 10 — API function

```ts
// In client.ts
import type { ReleaseTrendPoint } from "@/types";

export const getReleaseTrend = (repoId: string) =>
  apiClient
    .get<ApiResponse<ReleaseTrendPoint[]>>(`/analytics/repos/${repoId}/release-trend`)
    .then(unwrap);
```

#### Step 11 — React Query hook

```ts
import { getReleaseTrend } from "@/lib/api/client";

export function useReleaseTrend(repoId: string | undefined) {
  return useQuery({
    queryKey: ["release-trend", repoId],
    queryFn: () => getReleaseTrend(repoId!),
    staleTime: ANALYTICS_STALE_MS,
    enabled: !!repoId,
  });
}
```

#### Step 12 — Chart in explore page

In `frontend/src/app/(app)/explore/page.tsx`, below the contributors card, add (only when the user is logged in and has the repo tracked):

```tsx
import { BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer } from "recharts";
import { useReleaseTrend } from "@/hooks/useAnalytics";

// Inside component, pass the tracked repoId if available:
const { data: releaseTrend } = useReleaseTrend(trackedRepoId);

// JSX:
{releaseTrend && releaseTrend.length > 0 && (
  <div className="bg-card border border-border rounded-xl p-6">
    <h2 className="font-semibold text-sm mb-4">Release Cadence (12 months)</h2>
    <ResponsiveContainer width="100%" height={160}>
      <BarChart data={releaseTrend} margin={{ top: 4, right: 4, left: -28, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
        <XAxis dataKey="month" tick={{ fontSize: 10 }} tickLine={false} axisLine={false}
          tickFormatter={(v: string) => v.slice(5)} />
        <YAxis tick={{ fontSize: 10 }} tickLine={false} axisLine={false} allowDecimals={false} />
        <Tooltip
          contentStyle={{ background: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: 8, fontSize: 12 }}
          formatter={(v: number) => [v, "releases"]}
        />
        <Bar dataKey="count" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} name="Releases" />
      </BarChart>
    </ResponsiveContainer>
  </div>
)}
```

---

## Feature 7 — Issues Sync & Analytics

**Type: Backend + Frontend**

### What it does
Syncs GitHub issues for tracked repositories and exposes analytics — open/closed counts, average time-to-close, and an age distribution chart — in a new "Issues" tab on the analytics page. This gives teams visibility into support burden and responsiveness alongside code metrics.

### Files to create
- `backend/src/main/resources/db/migration/V7__issues.sql`
- `backend/src/main/java/com/gitanalytics/ingestion/entity/Issue.java`
- `backend/src/main/java/com/gitanalytics/ingestion/repository/IssueRepository.java`
- `backend/src/main/java/com/gitanalytics/ingestion/dao/IssueDao.java`
- `backend/src/main/java/com/gitanalytics/ingestion/dao/impl/IssueDaoImpl.java`
- `backend/src/main/java/com/gitanalytics/analytics/dto/IssueAnalyticsDto.java`
- `frontend/src/app/(app)/analytics/_components/IssuesSection.tsx`

### Files to modify
- `backend/src/main/java/com/gitanalytics/ingestion/client/GitHubApiClient.java` — add `getIssues()`
- `backend/src/main/java/com/gitanalytics/ingestion/service/RepoService.java` — sync issues
- `backend/src/main/java/com/gitanalytics/analytics/controller/AnalyticsController.java` — add endpoint
- `backend/src/main/java/com/gitanalytics/analytics/service/AnalyticsService.java` — add service method
- `backend/src/main/java/com/gitanalytics/analytics/dao/AnalyticsDao.java` — add DAO method
- `backend/src/main/java/com/gitanalytics/analytics/dao/impl/AnalyticsDaoImpl.java` — implement
- `backend/src/main/java/com/gitanalytics/analytics/repository/AnalyticsRepository.java` — add query
- `frontend/src/types/index.ts` — add type
- `frontend/src/lib/api/client.ts` — add API function
- `frontend/src/hooks/useAnalytics.ts` — add hook
- `frontend/src/app/(app)/analytics/page.tsx` — add Issues tab

### Step-by-step implementation

#### Step 1 — Flyway migration

```sql
-- V7__issues.sql
CREATE TABLE IF NOT EXISTS issues (
    id              BIGSERIAL PRIMARY KEY,
    repo_id         UUID NOT NULL REFERENCES tracked_repos(id) ON DELETE CASCADE,
    issue_number    INT NOT NULL,
    title           VARCHAR(500),
    author_login    VARCHAR(100),
    state           VARCHAR(20) NOT NULL DEFAULT 'open',   -- 'open' | 'closed'
    created_at      TIMESTAMPTZ NOT NULL,
    closed_at       TIMESTAMPTZ,
    UNIQUE (repo_id, issue_number)
);

CREATE INDEX IF NOT EXISTS idx_issues_repo_state ON issues(repo_id, state);
CREATE INDEX IF NOT EXISTS idx_issues_repo_created ON issues(repo_id, created_at DESC);
```

#### Step 2 — JPA Entity

```java
// Issue.java
package com.gitanalytics.ingestion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "issues")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Issue {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private TrackedRepo repo;

    @Column(name = "issue_number", nullable = false)
    private Integer issueNumber;

    @Column(length = 500)
    private String title;

    @Column(name = "author_login")
    private String authorLogin;

    @Column(nullable = false)
    private String state;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;
}
```

#### Step 3 — Repository

```java
// IssueRepository.java
package com.gitanalytics.ingestion.repository;

import com.gitanalytics.ingestion.entity.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {

    @Transactional
    @Modifying
    @Query(value = """
        INSERT INTO issues (repo_id, issue_number, title, author_login, state, created_at, closed_at)
        VALUES (:#{#i.repo.id}, :#{#i.issueNumber}, :#{#i.title}, :#{#i.authorLogin},
                :#{#i.state}, :#{#i.createdAt}, :#{#i.closedAt})
        ON CONFLICT (repo_id, issue_number) DO UPDATE
          SET title        = EXCLUDED.title,
              state        = EXCLUDED.state,
              closed_at    = EXCLUDED.closed_at
        """, nativeQuery = true)
    void upsert(@org.springframework.data.repository.query.Param("i") Issue issue);
}
```

#### Step 4 — DAO interface and impl

```java
// IssueDao.java
package com.gitanalytics.ingestion.dao;

import com.gitanalytics.ingestion.entity.Issue;

public interface IssueDao {
    void upsert(Issue issue);
}
```

```java
// IssueDaoImpl.java
package com.gitanalytics.ingestion.dao.impl;

import com.gitanalytics.ingestion.dao.IssueDao;
import com.gitanalytics.ingestion.entity.Issue;
import com.gitanalytics.ingestion.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class IssueDaoImpl implements IssueDao {
    private final IssueRepository issueRepository;

    @Override
    public void upsert(Issue issue) {
        issueRepository.upsert(issue);
    }
}
```

#### Step 5 — Add `getIssues()` to `GitHubApiClient`

```java
@Data
public static class GitHubIssueDto {
    @JsonProperty("number")     private Integer number;
    @JsonProperty("title")      private String title;
    @JsonProperty("state")      private String state;
    @JsonProperty("created_at") private OffsetDateTime createdAt;
    @JsonProperty("closed_at")  private OffsetDateTime closedAt;
    @JsonProperty("user")       private GitHubUserDto user;
    @JsonProperty("pull_request") private Object pullRequest; // non-null means it's a PR, skip it
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
        // GitHub issues API also returns PRs — filter them out
        all.addAll(batch.stream().filter(i -> i.getPullRequest() == null).toList());
        if (batch.size() < PER_PAGE) break;
        page++;
    }
    return all;
}
```

#### Step 6 — Sync issues in `RepoService`

After the release sync block, add:

```java
OffsetDateTime issuesSince = trackedRepo.getLastSyncedAt() != null
    ? trackedRepo.getLastSyncedAt().minusDays(1) : null;
List<GitHubApiClient.GitHubIssueDto> ghIssues =
    gitHubApiClient.getIssues(accessToken, userId, repo.getOwner(), repo.getName(), issuesSince);
for (GitHubApiClient.GitHubIssueDto gi : ghIssues) {
    Issue issue = Issue.builder()
        .repo(trackedRepo)
        .issueNumber(gi.getNumber())
        .title(gi.getTitle())
        .authorLogin(gi.getUser() != null ? gi.getUser().getLogin() : null)
        .state(gi.getState())
        .createdAt(gi.getCreatedAt())
        .closedAt(gi.getClosedAt())
        .build();
    issueDao.upsert(issue);
}
```

#### Step 7 — Analytics DTO

```java
// IssueAnalyticsDto.java
package com.gitanalytics.analytics.dto;

import java.util.List;

public record IssueAnalyticsDto(
    long openCount,
    long closedCount,
    Double avgCloseTimeDays,
    List<AgeBucket> ageDistribution
) {
    public record AgeBucket(String label, long count) {}
}
```

#### Step 8 — AnalyticsRepository queries

```java
// In AnalyticsRepository.java, add:

@Query(value = """
    SELECT
        COUNT(*) FILTER (WHERE state = 'open')   AS open_count,
        COUNT(*) FILTER (WHERE state = 'closed') AS closed_count,
        AVG(EXTRACT(EPOCH FROM (closed_at - created_at)) / 86400.0)
            FILTER (WHERE state = 'closed' AND closed_at IS NOT NULL) AS avg_close_days
    FROM issues
    WHERE repo_id = :repoId
    """, nativeQuery = true)
Object[] getIssueStats(UUID repoId);

@Query(value = """
    SELECT
        CASE
            WHEN NOW() - created_at < INTERVAL '1 day'   THEN '< 1 day'
            WHEN NOW() - created_at < INTERVAL '7 days'  THEN '1–7 days'
            WHEN NOW() - created_at < INTERVAL '30 days' THEN '7–30 days'
            WHEN NOW() - created_at < INTERVAL '90 days' THEN '30–90 days'
            ELSE '> 90 days'
        END AS age_bucket,
        COUNT(*) AS cnt
    FROM issues
    WHERE repo_id = :repoId AND state = 'open'
    GROUP BY age_bucket
    ORDER BY MIN(NOW() - created_at) ASC
    """, nativeQuery = true)
List<Object[]> getIssueAgeDistribution(UUID repoId);
```

#### Step 9 — AnalyticsDao

In `AnalyticsDao.java` add:

```java
IssueAnalyticsDto getIssueAnalytics(UUID repoId);
```

In `AnalyticsDaoImpl.java` add:

```java
@Override
public IssueAnalyticsDto getIssueAnalytics(UUID repoId) {
    Object[] stats = firstRow(analyticsRepository.getIssueStats(repoId));
    long open   = stats[0] != null ? ((Number) stats[0]).longValue() : 0;
    long closed = stats[1] != null ? ((Number) stats[1]).longValue() : 0;
    Double avg  = stats[2] != null ? ((Number) stats[2]).doubleValue() : null;

    List<IssueAnalyticsDto.AgeBucket> buckets = analyticsRepository
        .getIssueAgeDistribution(repoId).stream()
        .map(r -> new IssueAnalyticsDto.AgeBucket((String) r[0], ((Number) r[1]).longValue()))
        .toList();

    return new IssueAnalyticsDto(open, closed, avg, buckets);
}
```

#### Step 10 — AnalyticsService

```java
public IssueAnalyticsDto getIssueAnalytics(UUID userId, UUID repoId) {
    trackedRepoDao.findById(repoId)
        .filter(r -> r.getUser().getId().equals(userId))
        .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
    return analyticsDao.getIssueAnalytics(repoId);
}
```

#### Step 11 — AnalyticsController

```java
@GetMapping("/repos/{repoId}/issues")
public ResponseEntity<ApiResponse<IssueAnalyticsDto>> getIssueAnalytics(
        @AuthenticationPrincipal UserDetails principal,
        @PathVariable UUID repoId) {
    UUID userId = UUID.fromString(principal.getUsername());
    return ResponseEntity.ok(ApiResponse.ok(analyticsService.getIssueAnalytics(userId, repoId)));
}
```

#### Step 12 — Frontend type

```ts
// In types/index.ts
export interface IssueAnalytics {
  openCount: number;
  closedCount: number;
  avgCloseTimeDays: number | null;
  ageDistribution: Array<{ label: string; count: number }>;
}
```

#### Step 13 — API function

```ts
import type { IssueAnalytics } from "@/types";

export const getIssueAnalytics = (repoId: string) =>
  apiClient
    .get<ApiResponse<IssueAnalytics>>(`/analytics/repos/${repoId}/issues`)
    .then(unwrap);
```

#### Step 14 — React Query hook

```ts
import { getIssueAnalytics } from "@/lib/api/client";

export function useIssueAnalytics(repoId: string | undefined) {
  return useQuery({
    queryKey: ["issue-analytics", repoId],
    queryFn: () => getIssueAnalytics(repoId!),
    staleTime: ANALYTICS_STALE_MS,
    enabled: !!repoId,
  });
}
```

#### Step 15 — IssuesSection component

```tsx
// frontend/src/app/(app)/analytics/_components/IssuesSection.tsx
"use client";

import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from "recharts";
import type { IssueAnalytics } from "@/types";

interface Props {
  data: IssueAnalytics;
  isLoading: boolean;
}

function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse bg-muted rounded ${className}`} />;
}

export function IssuesSection({ data, isLoading }: Props) {
  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="grid grid-cols-3 gap-4">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
        <Skeleton className="h-52" />
      </div>
    );
  }

  const total = data.openCount + data.closedCount;
  const closedPct = total > 0 ? Math.round((data.closedCount / total) * 100) : 0;

  return (
    <div className="space-y-6">
      {/* Summary cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-card border border-border rounded-xl p-5">
          <div className="text-xs text-muted-foreground uppercase tracking-wide mb-1">Open Issues</div>
          <div className="text-2xl font-bold text-orange-500">{data.openCount.toLocaleString()}</div>
        </div>
        <div className="bg-card border border-border rounded-xl p-5">
          <div className="text-xs text-muted-foreground uppercase tracking-wide mb-1">Closed Issues</div>
          <div className="text-2xl font-bold text-green-600">{data.closedCount.toLocaleString()}</div>
          <div className="text-xs text-muted-foreground mt-1">{closedPct}% closure rate</div>
        </div>
        <div className="bg-card border border-border rounded-xl p-5">
          <div className="text-xs text-muted-foreground uppercase tracking-wide mb-1">Avg Close Time</div>
          <div className="text-2xl font-bold">
            {data.avgCloseTimeDays != null
              ? `${data.avgCloseTimeDays.toFixed(1)}d`
              : "—"}
          </div>
        </div>
      </div>

      {/* Age distribution chart */}
      {data.ageDistribution.length > 0 && (
        <div className="bg-card border border-border rounded-xl p-6">
          <h3 className="font-semibold text-sm mb-4">Open Issue Age Distribution</h3>
          <ResponsiveContainer width="100%" height={180}>
            <BarChart
              data={data.ageDistribution}
              margin={{ top: 4, right: 4, left: -24, bottom: 0 }}
            >
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
              <XAxis dataKey="label" tick={{ fontSize: 10 }} tickLine={false} axisLine={false} />
              <YAxis tick={{ fontSize: 10 }} tickLine={false} axisLine={false} allowDecimals={false} />
              <Tooltip
                contentStyle={{
                  background: "hsl(var(--card))",
                  border: "1px solid hsl(var(--border))",
                  borderRadius: 8,
                  fontSize: 12,
                }}
                formatter={(v: number) => [v, "issues"]}
              />
              <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                {data.ageDistribution.map((entry) => (
                  <Cell
                    key={entry.label}
                    fill={
                      entry.label === "< 1 day"   ? "#10b981" :
                      entry.label === "1–7 days"  ? "#3b82f6" :
                      entry.label === "7–30 days" ? "#f59e0b" :
                      entry.label === "30–90 days"? "#f97316" :
                      "#ef4444"
                    }
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
}
```

#### Step 16 — Add Issues tab to analytics page

In `frontend/src/app/(app)/analytics/page.tsx`, add a tab:

```tsx
import { IssuesSection } from "./_components/IssuesSection";
import { useIssueAnalytics } from "@/hooks/useAnalytics";

// Add "issues" to the tab list:
const TABS = ["overview", "commits", "prs", "reviews", "issues"] as const;

// Conditionally fetch:
const { data: issueData, isLoading: issueLoading } = useIssueAnalytics(
  activeTab === "issues" ? selectedRepoId : undefined
);

// Render tab panel:
{activeTab === "issues" && (
  <IssuesSection
    data={issueData ?? { openCount: 0, closedCount: 0, avgCloseTimeDays: null, ageDistribution: [] }}
    isLoading={issueLoading}
  />
)}
```

---

## Feature 12 — Language Bytes Upgrade

**Type: Backend + Frontend**

### What it does
Replaces the coarse "primary language" field with byte-accurate language breakdowns per repo, fetched from GitHub's `/languages` API endpoint during sync. The `LanguageDistribution` pie chart is upgraded to show byte-weighted percentages instead of a simple repo count, making cross-repo language distribution meaningful.

### Files to create
- `backend/src/main/resources/db/migration/V8__repo_languages.sql`
- `backend/src/main/java/com/gitanalytics/ingestion/entity/RepoLanguage.java`
- `backend/src/main/java/com/gitanalytics/ingestion/repository/RepoLanguageRepository.java`
- `backend/src/main/java/com/gitanalytics/analytics/dto/RepoLanguageDto.java`

### Files to modify
- `backend/src/main/java/com/gitanalytics/ingestion/client/GitHubApiClient.java` — add `getLanguages()`
- `backend/src/main/java/com/gitanalytics/ingestion/service/RepoService.java` — sync languages
- `backend/src/main/java/com/gitanalytics/analytics/controller/AnalyticsController.java` — add endpoint
- `backend/src/main/java/com/gitanalytics/analytics/service/AnalyticsService.java` — add service method
- `backend/src/main/java/com/gitanalytics/analytics/dao/AnalyticsDao.java` — add DAO method
- `backend/src/main/java/com/gitanalytics/analytics/dao/impl/AnalyticsDaoImpl.java` — implement
- `backend/src/main/java/com/gitanalytics/analytics/repository/AnalyticsRepository.java` — add query
- `frontend/src/types/index.ts` — add type
- `frontend/src/lib/api/client.ts` — add API function
- `frontend/src/hooks/useAnalytics.ts` — add hook
- `frontend/src/app/(app)/analytics/_components/LanguageDistribution.tsx` — upgrade chart

### Step-by-step implementation

#### Step 1 — Migration

```sql
-- V8__repo_languages.sql
CREATE TABLE IF NOT EXISTS repo_languages (
    id          BIGSERIAL PRIMARY KEY,
    repo_id     UUID NOT NULL REFERENCES tracked_repos(id) ON DELETE CASCADE,
    language    VARCHAR(100) NOT NULL,
    bytes       BIGINT NOT NULL DEFAULT 0,
    UNIQUE (repo_id, language)
);

CREATE INDEX IF NOT EXISTS idx_repo_languages_repo ON repo_languages(repo_id);
```

#### Step 2 — Entity

```java
// RepoLanguage.java
package com.gitanalytics.ingestion.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "repo_languages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RepoLanguage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private TrackedRepo repo;

    @Column(nullable = false)
    private String language;

    private long bytes;
}
```

#### Step 3 — Repository

```java
// RepoLanguageRepository.java
package com.gitanalytics.ingestion.repository;

import com.gitanalytics.ingestion.entity.RepoLanguage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface RepoLanguageRepository extends JpaRepository<RepoLanguage, Long> {

    @Transactional
    @Modifying
    @Query(value = """
        INSERT INTO repo_languages (repo_id, language, bytes)
        VALUES (:#{#rl.repo.id}, :#{#rl.language}, :#{#rl.bytes})
        ON CONFLICT (repo_id, language) DO UPDATE SET bytes = EXCLUDED.bytes
        """, nativeQuery = true)
    void upsert(@org.springframework.data.repository.query.Param("rl") RepoLanguage repoLanguage);

    @Query(value = """
        SELECT language, SUM(bytes) AS total_bytes
        FROM repo_languages
        WHERE repo_id IN (
            SELECT id FROM tracked_repos WHERE user_id = :userId
        )
        GROUP BY language
        ORDER BY total_bytes DESC
        """, nativeQuery = true)
    java.util.List<Object[]> aggregateBytesByUserAndLanguage(UUID userId);

    @Query(value = """
        SELECT language, bytes
        FROM repo_languages
        WHERE repo_id = :repoId
        ORDER BY bytes DESC
        """, nativeQuery = true)
    java.util.List<Object[]> findByRepoId(UUID repoId);
}
```

#### Step 4 — DTO

```java
// RepoLanguageDto.java
package com.gitanalytics.analytics.dto;

public record RepoLanguageDto(String language, long bytes, double pct) {}
```

#### Step 5 — `getLanguages()` in GitHubApiClient

GitHub's `/languages` endpoint returns a flat JSON object like `{"TypeScript": 123456, "CSS": 5000}`. Use `Map`:

```java
public Map<String, Long> getLanguages(String accessToken, UUID userId,
                                       String owner, String repo) {
    checkRateLimit(userId);
    try {
        @SuppressWarnings("unchecked")
        Map<String, Long> result = (Map<String, Long>) webClientBuilder.build()
            .get()
            .uri(GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/languages")
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/vnd.github+json")
            .retrieve()
            .bodyToMono(Map.class)
            .block();
        return result != null ? result : Map.of();
    } catch (Exception e) {
        log.warn("Could not fetch languages for {}/{}: {}", owner, repo, e.getMessage());
        return Map.of();
    }
}
```

#### Step 6 — Sync languages in RepoService

After issue sync block:

```java
Map<String, Long> ghLanguages = gitHubApiClient.getLanguages(accessToken, userId,
    repo.getOwner(), repo.getName());
for (Map.Entry<String, Long> entry : ghLanguages.entrySet()) {
    RepoLanguage rl = RepoLanguage.builder()
        .repo(trackedRepo)
        .language(entry.getKey())
        .bytes(entry.getValue())
        .build();
    repoLanguageRepository.upsert(rl);
}
```

Inject `RepoLanguageRepository` into the service (or route through a new `RepoLanguageDao`).

#### Step 7 — AnalyticsRepository queries

```java
// Already added to RepoLanguageRepository — call from AnalyticsRepository or directly.
// Add to AnalyticsRepository.java:

@Query(value = """
    SELECT rl.language, SUM(rl.bytes) AS total_bytes
    FROM repo_languages rl
    JOIN tracked_repos tr ON tr.id = rl.repo_id
    WHERE tr.user_id = :userId
    GROUP BY rl.language
    ORDER BY total_bytes DESC
    """, nativeQuery = true)
List<Object[]> getLanguageBytesForUser(UUID userId);
```

#### Step 8 — AnalyticsDao + impl

In `AnalyticsDao.java`:

```java
List<RepoLanguageDto> getLanguageBytes(UUID userId);
```

In `AnalyticsDaoImpl.java`:

```java
@Override
public List<RepoLanguageDto> getLanguageBytes(UUID userId) {
    List<Object[]> rows = analyticsRepository.getLanguageBytesForUser(userId);
    long totalBytes = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
    if (totalBytes == 0) return List.of();
    return rows.stream().map(r -> {
        long bytes = ((Number) r[1]).longValue();
        return new RepoLanguageDto((String) r[0], bytes,
            Math.round((double) bytes / totalBytes * 1000.0) / 10.0);
    }).toList();
}
```

#### Step 9 — AnalyticsService + Controller

```java
// AnalyticsService:
public List<RepoLanguageDto> getLanguageBytes(UUID userId) {
    return analyticsDao.getLanguageBytes(userId);
}
```

```java
// AnalyticsController:
@GetMapping("/language-bytes")
public ResponseEntity<ApiResponse<List<RepoLanguageDto>>> getLanguageBytes(
        @AuthenticationPrincipal UserDetails principal) {
    UUID userId = UUID.fromString(principal.getUsername());
    return ResponseEntity.ok(ApiResponse.ok(analyticsService.getLanguageBytes(userId)));
}
```

#### Step 10 — Frontend type

```ts
// In types/index.ts
export interface RepoLanguageStat {
  language: string;
  bytes: number;
  pct: number;
}
```

#### Step 11 — API function + hook

```ts
// client.ts
import type { RepoLanguageStat } from "@/types";

export const getLanguageBytes = () =>
  apiClient.get<ApiResponse<RepoLanguageStat[]>>("/analytics/language-bytes").then(unwrap);
```

```ts
// useAnalytics.ts
import { getLanguageBytes } from "@/lib/api/client";

export function useLanguageBytes() {
  return useQuery({
    queryKey: ["language-bytes"],
    queryFn: getLanguageBytes,
    staleTime: ANALYTICS_STALE_MS,
  });
}
```

#### Step 12 — Upgrade `LanguageDistribution` component

The existing component in `frontend/src/app/(app)/analytics/_components/LanguageDistribution.tsx` uses repo count. Extend it to accept an optional `byteStats` prop:

```tsx
// New prop signature:
interface Props {
  repos: Repo[];
  byteStats?: RepoLanguageStat[];   // from /analytics/language-bytes
}

export function LanguageDistribution({ repos, byteStats }: Props) {
  const stats = useMemo<LangStat[]>(() => {
    // If byte-accurate data is available, prefer it
    if (byteStats && byteStats.length > 0) {
      return byteStats.map((s, idx) => ({
        language: s.language,
        count: s.bytes,          // use bytes as the chart value
        pct: s.pct,
        color: getColor(s.language, idx),
      }));
    }
    // Fallback: original repo-count logic
    const map: Record<string, number> = {};
    for (const repo of repos) {
      const lang = repo.language ?? "Unknown";
      map[lang] = (map[lang] ?? 0) + 1;
    }
    const total = repos.length || 1;
    return Object.entries(map)
      .sort((a, b) => b[1] - a[1])
      .map(([language, count], idx) => ({
        language, count, pct: Math.round((count / total) * 100), color: getColor(language, idx),
      }));
  }, [repos, byteStats]);

  // Update tooltip to show bytes when in byte mode:
  const CustomTooltip = ({ active, payload }: { active?: boolean; payload?: { payload: LangStat }[] }) => {
    if (!active || !payload?.length) return null;
    const { language, count, pct } = payload[0].payload;
    const label = byteStats
      ? `${(count / 1024).toFixed(1)} KB · ${pct}%`
      : `${count} repo${count !== 1 ? "s" : ""} · ${pct}%`;
    return (
      <div className="bg-card border border-border text-foreground text-xs px-3 py-2 rounded-lg shadow-lg">
        <div className="font-semibold">{language}</div>
        <div className="text-muted-foreground mt-0.5">{label}</div>
      </div>
    );
  };

  // ... rest of JSX unchanged, but update the legend line to show bytes if byteStats present:
  // <span className="text-xs text-muted-foreground w-20 text-right shrink-0">
  //   {byteStats ? `${(s.count/1024).toFixed(0)}KB` : `${s.count} repo${s.count !== 1 ? 's' : ''}`} · {s.pct}%
  // </span>
}
```

---

## Feature 11 — Compare Two Repos

**Type: Backend + Frontend**

### What it does
Lets a user select any two of their tracked repositories and view a side-by-side health and activity comparison, making it easy to see which projects need more attention. A new "Repositories" tab appears on the existing `/compare` page, reusing all the existing `CompareCard` and delta badge components.

### Files to create
- `backend/src/main/java/com/gitanalytics/analytics/dto/RepoCompareDto.java`

### Files to modify
- `backend/src/main/java/com/gitanalytics/analytics/controller/AnalyticsController.java` — add endpoint
- `backend/src/main/java/com/gitanalytics/analytics/service/AnalyticsService.java` — add service method
- `frontend/src/types/index.ts` — add type
- `frontend/src/lib/api/client.ts` — add API function
- `frontend/src/hooks/useAnalytics.ts` — add hook
- `frontend/src/app/(app)/compare/page.tsx` — add "Repositories" tab

### Step-by-step implementation

#### Step 1 — DTO

```java
// RepoCompareDto.java
package com.gitanalytics.analytics.dto;

public record RepoCompareDto(
    String repoId,
    String fullName,
    int healthScore,
    String healthLabel,
    long totalCommits,
    long totalPRs,
    long mergedPRs,
    double avgMergeTimeHours,
    String topContributor,
    int topContributorPct,
    int stars,
    int forks,
    int openIssues
) {}
```

#### Step 2 — AnalyticsService

```java
public List<RepoCompareDto> compareRepos(UUID userId, UUID repoIdA, UUID repoIdB) {
    return List.of(
        buildRepoCompareDto(userId, repoIdA),
        buildRepoCompareDto(userId, repoIdB)
    );
}

private RepoCompareDto buildRepoCompareDto(UUID userId, UUID repoId) {
    var repo = trackedRepoDao.findById(repoId)
        .filter(r -> r.getUser().getId().equals(userId))
        .orElseThrow(() -> new ResourceNotFoundException("Repository not found: " + repoId));

    RepoHealthDto health = getRepoHealth(userId, repoId);
    long totalCommits = analyticsDao.countCommitsByRepo(repoId);

    Object[] prStats = analyticsDao.getPRStatsByRepo(repoId);
    long totalPRs  = ((Number) prStats[0]).longValue();
    long mergedPRs = ((Number) prStats[1]).longValue();
    double avgMerge = prStats[2] != null ? ((Number) prStats[2]).doubleValue() : 0;

    List<Object[]> topRows = analyticsDao.getTopContributorByRepo(repoId);
    String topContributor = topRows.isEmpty() ? null : (String) topRows.get(0)[0];
    long topCount = topRows.isEmpty() ? 0 : ((Number) topRows.get(0)[1]).longValue();
    int topPct = totalCommits > 0 ? (int) (topCount * 100 / totalCommits) : 0;

    return new RepoCompareDto(
        repoId.toString(),
        repo.getFullName(),
        health.score(),
        health.label(),
        totalCommits,
        totalPRs,
        mergedPRs,
        avgMerge,
        topContributor,
        topPct,
        repo.getStars()          != null ? repo.getStars()          : 0,
        repo.getForks()          != null ? repo.getForks()          : 0,
        repo.getOpenIssuesCount() != null ? repo.getOpenIssuesCount() : 0
    );
}
```

#### Step 3 — AnalyticsController endpoint

```java
@GetMapping("/repos/compare")
public ResponseEntity<ApiResponse<List<RepoCompareDto>>> compareRepos(
        @AuthenticationPrincipal UserDetails principal,
        @RequestParam UUID repoIdA,
        @RequestParam UUID repoIdB) {
    UUID userId = UUID.fromString(principal.getUsername());
    return ResponseEntity.ok(ApiResponse.ok(analyticsService.compareRepos(userId, repoIdA, repoIdB)));
}
```

#### Step 4 — Frontend type

```ts
// In types/index.ts
export interface RepoCompare {
  repoId: string;
  fullName: string;
  healthScore: number;
  healthLabel: string;
  totalCommits: number;
  totalPRs: number;
  mergedPRs: number;
  avgMergeTimeHours: number;
  topContributor: string | null;
  topContributorPct: number;
  stars: number;
  forks: number;
  openIssues: number;
}
```

#### Step 5 — API function

```ts
import type { RepoCompare } from "@/types";

export const compareRepos = (repoIdA: string, repoIdB: string) =>
  apiClient
    .get<ApiResponse<RepoCompare[]>>("/analytics/repos/compare", {
      params: { repoIdA, repoIdB },
    })
    .then(unwrap);
```

#### Step 6 — React Query hook

```ts
import { compareRepos } from "@/lib/api/client";

export function useRepoCompare(
  repoIdA: string | undefined,
  repoIdB: string | undefined
) {
  return useQuery({
    queryKey: ["repo-compare", repoIdA, repoIdB],
    queryFn: () => compareRepos(repoIdA!, repoIdB!),
    staleTime: ANALYTICS_STALE_MS,
    enabled: !!repoIdA && !!repoIdB,
  });
}
```

#### Step 7 — "Repositories" tab in compare page

In `frontend/src/app/(app)/compare/page.tsx`, add a tab switcher at the top:

```tsx
import { useRepoCompare } from "@/hooks/useAnalytics";
import { useRepos } from "@/hooks/useRepos"; // existing hook

// Tab state:
const [compareTab, setCompareTab] = useState<"periods" | "repos">("periods");
const [repoAId, setRepoAId] = useState<string>("");
const [repoBId, setRepoBId] = useState<string>("");

const { data: repos } = useRepos();
const { data: repoCompare, isLoading: rcLoading } = useRepoCompare(
  repoAId || undefined, repoBId || undefined
);

// Tab bar (add above the PRESETS section):
<div className="flex gap-2 border-b border-border pb-4">
  <button
    onClick={() => setCompareTab("periods")}
    className={`px-4 py-2 text-sm rounded-lg font-medium transition-colors ${
      compareTab === "periods"
        ? "bg-primary text-primary-foreground"
        : "text-muted-foreground hover:text-foreground hover:bg-muted/40"
    }`}
  >
    Periods
  </button>
  <button
    onClick={() => setCompareTab("repos")}
    className={`px-4 py-2 text-sm rounded-lg font-medium transition-colors ${
      compareTab === "repos"
        ? "bg-primary text-primary-foreground"
        : "text-muted-foreground hover:text-foreground hover:bg-muted/40"
    }`}
  >
    Repositories
  </button>
</div>

// Repo compare panel (shown when compareTab === "repos"):
{compareTab === "repos" && (
  <div className="space-y-6">
    {/* Repo selectors */}
    <div className="grid grid-cols-2 gap-4">
      {[
        { label: "Repo A", value: repoAId, set: setRepoAId },
        { label: "Repo B", value: repoBId, set: setRepoBId },
      ].map(({ label, value, set }) => (
        <div key={label} className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground">{label}</label>
          <select
            value={value}
            onChange={(e) => set(e.target.value)}
            className="border border-border bg-background text-foreground text-sm rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/50"
          >
            <option value="">Select a repo…</option>
            {(repos ?? []).map((r) => (
              <option key={r.id} value={r.id}>{r.fullName}</option>
            ))}
          </select>
        </div>
      ))}
    </div>

    {/* Comparison grid */}
    {rcLoading ? (
      <div className="grid grid-cols-2 gap-4 animate-pulse">
        {Array.from({ length: 10 }).map((_, i) => (
          <div key={i} className="h-20 bg-muted rounded-xl" />
        ))}
      </div>
    ) : repoCompare && repoCompare.length === 2 && (
      <div className="space-y-4">
        {/* Repo name headers */}
        <div className="grid grid-cols-2 gap-4 text-center">
          <div className="bg-primary/10 border border-primary/30 rounded-xl px-4 py-2 text-sm font-semibold">
            {repoCompare[0].fullName}
          </div>
          <div className="bg-muted/30 border border-border rounded-xl px-4 py-2 text-sm font-semibold">
            {repoCompare[1].fullName}
          </div>
        </div>

        {/* Metric rows */}
        {[
          { label: "Health Score", keyA: "healthScore", keyB: "healthScore", suffix: "%" },
          { label: "Total Commits", keyA: "totalCommits", keyB: "totalCommits" },
          { label: "Total PRs", keyA: "totalPRs", keyB: "totalPRs" },
          { label: "Merged PRs", keyA: "mergedPRs", keyB: "mergedPRs" },
          { label: "Stars", keyA: "stars", keyB: "stars" },
          { label: "Forks", keyA: "forks", keyB: "forks" },
        ].map(({ label, keyA }) => {
          const vA = repoCompare[0][keyA as keyof typeof repoCompare[0]] as number;
          const vB = repoCompare[1][keyA as keyof typeof repoCompare[1]] as number;
          return (
            <CompareCard
              key={label}
              label={label}
              aValue={vA}
              bValue={vB}
              pct={delta(vA, vB)}
              aLabel={repoCompare[0].fullName.split("/")[1]}
              bLabel={repoCompare[1].fullName.split("/")[1]}
              isLoading={false}
            />
          );
        })}
      </div>
    )}
  </div>
)}
```

---

## Feature 10 — Compare Two Contributors

**Type: Backend + Frontend**

### What it does
Allows a team lead to pick two contributors from a repository and compare their commit count, lines changed, PR throughput, and merge time side by side with delta badges. The feature adds a new "Contributors" tab to the existing `/compare` page and a new backend endpoint that returns stats for two logins in one call.

### Files to create
- `backend/src/main/java/com/gitanalytics/analytics/dto/ContributorCompareDto.java`

### Files to modify
- `backend/src/main/java/com/gitanalytics/analytics/controller/AnalyticsController.java` — add endpoint
- `backend/src/main/java/com/gitanalytics/analytics/service/AnalyticsService.java` — add service method
- `backend/src/main/java/com/gitanalytics/analytics/dao/AnalyticsDao.java` — add DAO method
- `backend/src/main/java/com/gitanalytics/analytics/dao/impl/AnalyticsDaoImpl.java` — implement
- `backend/src/main/java/com/gitanalytics/analytics/repository/AnalyticsRepository.java` — add query
- `frontend/src/types/index.ts` — add type
- `frontend/src/lib/api/client.ts` — add API function
- `frontend/src/hooks/useAnalytics.ts` — add hook
- `frontend/src/app/(app)/compare/page.tsx` — add "Contributors" tab

### Step-by-step implementation

#### Step 1 — DTO

```java
// ContributorCompareDto.java
package com.gitanalytics.analytics.dto;

public record ContributorCompareDto(
    String login,
    long commits,
    long linesAdded,
    long linesRemoved,
    long prsAuthored,
    long prsMerged,
    Double avgMergeTimeHours,
    long reviewsGiven
) {}
```

#### Step 2 — AnalyticsRepository query

```java
@Query(value = """
    SELECT
        c.author_login                         AS login,
        COUNT(c.id)                            AS commits,
        COALESCE(SUM(c.additions), 0)          AS lines_added,
        COALESCE(SUM(c.deletions), 0)          AS lines_removed
    FROM commits c
    JOIN tracked_repos tr ON tr.id = c.repo_id
    WHERE tr.id   = :repoId
      AND tr.user_id = :userId
      AND c.author_login = :login
      AND c.committed_at BETWEEN :from AND :to
    GROUP BY c.author_login
    """, nativeQuery = true)
Object[] getContributorCommitStats(UUID userId, UUID repoId, String login,
                                    OffsetDateTime from, OffsetDateTime to);

@Query(value = """
    SELECT
        pr.author_login                        AS login,
        COUNT(pr.id)                           AS prs_authored,
        COUNT(pr.id) FILTER (WHERE pr.state = 'MERGED') AS prs_merged,
        AVG(EXTRACT(EPOCH FROM (pr.merged_at - pr.created_at)) / 3600.0)
            FILTER (WHERE pr.merged_at IS NOT NULL)      AS avg_merge_hours
    FROM pull_requests pr
    JOIN tracked_repos tr ON tr.id = pr.repo_id
    WHERE tr.id = :repoId
      AND tr.user_id = :userId
      AND pr.author_login = :login
      AND pr.created_at BETWEEN :from AND :to
    GROUP BY pr.author_login
    """, nativeQuery = true)
Object[] getContributorPRStats(UUID userId, UUID repoId, String login,
                                OffsetDateTime from, OffsetDateTime to);

@Query(value = """
    SELECT COUNT(rv.id)
    FROM pr_reviews rv
    JOIN pull_requests pr ON pr.id = rv.pull_request_id
    JOIN tracked_repos tr ON tr.id = pr.repo_id
    WHERE tr.id = :repoId
      AND tr.user_id = :userId
      AND rv.reviewer_login = :login
      AND rv.submitted_at BETWEEN :from AND :to
    """, nativeQuery = true)
Long getContributorReviewCount(UUID userId, UUID repoId, String login,
                                OffsetDateTime from, OffsetDateTime to);
```

#### Step 3 — AnalyticsDao

In `AnalyticsDao.java`:

```java
ContributorCompareDto getContributorStats(UUID userId, UUID repoId, String login,
                                           OffsetDateTime from, OffsetDateTime to);
```

In `AnalyticsDaoImpl.java`:

```java
@Override
public ContributorCompareDto getContributorStats(UUID userId, UUID repoId, String login,
                                                   OffsetDateTime from, OffsetDateTime to) {
    Object[] cRow = analyticsRepository.getContributorCommitStats(userId, repoId, login, from, to);
    Object[] pRow = analyticsRepository.getContributorPRStats(userId, repoId, login, from, to);
    Long reviews = analyticsRepository.getContributorReviewCount(userId, repoId, login, from, to);

    // commits row may be null if contributor made no commits in range
    long commits     = cRow != null && cRow[1] != null ? ((Number) cRow[1]).longValue() : 0;
    long linesAdded  = cRow != null && cRow[2] != null ? ((Number) cRow[2]).longValue() : 0;
    long linesRemoved= cRow != null && cRow[3] != null ? ((Number) cRow[3]).longValue() : 0;
    long prsAuthored = pRow != null && pRow[1] != null ? ((Number) pRow[1]).longValue() : 0;
    long prsMerged   = pRow != null && pRow[2] != null ? ((Number) pRow[2]).longValue() : 0;
    Double avgMerge  = pRow != null && pRow[3] != null ? ((Number) pRow[3]).doubleValue() : null;

    return new ContributorCompareDto(
        login, commits, linesAdded, linesRemoved,
        prsAuthored, prsMerged, avgMerge,
        reviews != null ? reviews : 0
    );
}
```

#### Step 4 — AnalyticsService

```java
public List<ContributorCompareDto> compareContributors(UUID userId, UUID repoId,
                                                        String loginA, String loginB,
                                                        OffsetDateTime from, OffsetDateTime to) {
    trackedRepoDao.findById(repoId)
        .filter(r -> r.getUser().getId().equals(userId))
        .orElseThrow(() -> new ResourceNotFoundException("Repository not found"));
    return List.of(
        analyticsDao.getContributorStats(userId, repoId, loginA, from, to),
        analyticsDao.getContributorStats(userId, repoId, loginB, from, to)
    );
}
```

#### Step 5 — AnalyticsController

```java
@GetMapping("/contributor/compare")
public ResponseEntity<ApiResponse<List<ContributorCompareDto>>> compareContributors(
        @AuthenticationPrincipal UserDetails principal,
        @RequestParam UUID repoId,
        @RequestParam String loginA,
        @RequestParam String loginB,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
    UUID userId = UUID.fromString(principal.getUsername());
    return ResponseEntity.ok(ApiResponse.ok(
        analyticsService.compareContributors(userId, repoId, loginA, loginB, from, to)));
}
```

#### Step 6 — Frontend type

```ts
// In types/index.ts
export interface ContributorCompare {
  login: string;
  commits: number;
  linesAdded: number;
  linesRemoved: number;
  prsAuthored: number;
  prsMerged: number;
  avgMergeTimeHours: number | null;
  reviewsGiven: number;
}
```

#### Step 7 — API function

```ts
import type { ContributorCompare } from "@/types";

export const compareContributors = (
  repoId: string,
  loginA: string,
  loginB: string,
  from: string,
  to: string
) =>
  apiClient
    .get<ApiResponse<ContributorCompare[]>>("/analytics/contributor/compare", {
      params: { repoId, loginA, loginB, from, to },
    })
    .then(unwrap);
```

#### Step 8 — React Query hook

```ts
import { compareContributors } from "@/lib/api/client";

export function useContributorCompare(
  repoId: string | undefined,
  loginA: string | undefined,
  loginB: string | undefined,
  from: string,
  to: string
) {
  return useQuery({
    queryKey: ["contributor-compare", repoId, loginA, loginB, from, to],
    queryFn: () => compareContributors(repoId!, loginA!, loginB!, from, to),
    staleTime: ANALYTICS_STALE_MS,
    enabled: !!(repoId && loginA && loginB),
  });
}
```

#### Step 9 — "Contributors" tab in compare page

Add a third tab (`compareTab: "periods" | "repos" | "contributors"`) to the tab bar introduced in Feature 11.

```tsx
// Inside compareTab === "contributors" panel:
<div className="space-y-6">
  {/* Repo selector */}
  <div>
    <label className="text-xs text-muted-foreground">Repository</label>
    <select
      value={contribRepoId}
      onChange={(e) => setContribRepoId(e.target.value)}
      className="mt-1 w-full border border-border bg-background text-foreground text-sm rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/50"
    >
      <option value="">Select a repo…</option>
      {(repos ?? []).map((r) => (
        <option key={r.id} value={r.id}>{r.fullName}</option>
      ))}
    </select>
  </div>

  {/* Login inputs */}
  <div className="grid grid-cols-2 gap-4">
    {[
      { label: "Contributor A", value: loginA, set: setLoginA },
      { label: "Contributor B", value: loginB, set: setLoginB },
    ].map(({ label, value, set }) => (
      <div key={label} className="flex flex-col gap-1">
        <label className="text-xs text-muted-foreground">{label}</label>
        <input
          type="text"
          value={value}
          onChange={(e) => set(e.target.value)}
          placeholder="github-username"
          className="border border-border bg-background text-foreground text-sm rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/50"
        />
      </div>
    ))}
  </div>

  {/* Date range — reuse existing preset/custom pickers */}
  {/* ... reuse aFrom, aTo from existing period state */}

  {/* Results */}
  {ccLoading ? (
    <div className="grid grid-cols-2 gap-4 animate-pulse">
      {Array.from({ length: 8 }).map((_, i) => <div key={i} className="h-20 bg-muted rounded-xl" />)}
    </div>
  ) : contribCompare && contribCompare.length === 2 && (
    <div className="space-y-4">
      {[
        { label: "Commits", keyName: "commits" as const },
        { label: "Lines Added", keyName: "linesAdded" as const },
        { label: "PRs Authored", keyName: "prsAuthored" as const },
        { label: "PRs Merged", keyName: "prsMerged" as const },
        { label: "Reviews Given", keyName: "reviewsGiven" as const },
      ].map(({ label, keyName }) => {
        const vA = contribCompare[0][keyName] as number;
        const vB = contribCompare[1][keyName] as number;
        return (
          <CompareCard
            key={label}
            label={label}
            aValue={vA.toLocaleString()}
            bValue={vB.toLocaleString()}
            pct={delta(vA, vB)}
            aLabel={contribCompare[0].login}
            bLabel={contribCompare[1].login}
            isLoading={false}
          />
        );
      })}

      {/* Avg merge time — inverse metric */}
      <CompareCard
        label="Avg Merge Time"
        aValue={contribCompare[0].avgMergeTimeHours != null ? formatHours(contribCompare[0].avgMergeTimeHours) : "—"}
        bValue={contribCompare[1].avgMergeTimeHours != null ? formatHours(contribCompare[1].avgMergeTimeHours) : "—"}
        pct={delta(contribCompare[0].avgMergeTimeHours ?? 0, contribCompare[1].avgMergeTimeHours ?? 0)}
        aLabel={contribCompare[0].login}
        bLabel={contribCompare[1].login}
        isLoading={false}
        inverse
      />
    </div>
  )}
</div>
```

---

## Feature 13 — Public Shareable Snapshots

**Type: Backend + Frontend**

### What it does
Gives users a one-click "Share" button on the dashboard that creates a time-frozen analytics snapshot accessible via a public URL, with no login required. The generated link (`/share/{token}`) works like a read-only report card — perfect for sharing with managers or adding to a portfolio.

### Files to create
- `backend/src/main/resources/db/migration/V9__shared_snapshots.sql`
- `backend/src/main/java/com/gitanalytics/analytics/entity/SharedSnapshot.java`
- `backend/src/main/java/com/gitanalytics/analytics/repository/SharedSnapshotRepository.java`
- `backend/src/main/java/com/gitanalytics/analytics/dto/SnapshotPayload.java`
- `backend/src/main/java/com/gitanalytics/analytics/dto/CreateShareResponse.java`
- `backend/src/main/java/com/gitanalytics/analytics/controller/ShareController.java`
- `backend/src/main/java/com/gitanalytics/analytics/service/ShareService.java`
- `frontend/src/app/(public)/share/[token]/page.tsx`

### Files to modify
- `backend/src/main/java/com/gitanalytics/shared/config/SecurityConfig.java` — permit `/api/v1/public/share/**`
- `frontend/src/app/(app)/analytics/page.tsx` — add Share button
- `frontend/src/types/index.ts` — add types
- `frontend/src/lib/api/client.ts` — add API functions

### Step-by-step implementation

#### Step 1 — Migration

```sql
-- V9__shared_snapshots.sql
CREATE TABLE IF NOT EXISTS shared_snapshots (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(64) NOT NULL UNIQUE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    payload     JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '30 days'
);

CREATE INDEX IF NOT EXISTS idx_shared_snapshots_token ON shared_snapshots(token);
```

#### Step 2 — Entity

```java
// SharedSnapshot.java
package com.gitanalytics.analytics.entity;

import com.gitanalytics.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shared_snapshots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SharedSnapshot {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;  // serialised JSON of SnapshotPayload

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (expiresAt == null) expiresAt = createdAt.plusDays(30);
    }
}
```

#### Step 3 — Repository

```java
// SharedSnapshotRepository.java
package com.gitanalytics.analytics.repository;

import com.gitanalytics.analytics.entity.SharedSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SharedSnapshotRepository extends JpaRepository<SharedSnapshot, UUID> {
    Optional<SharedSnapshot> findByToken(String token);
}
```

#### Step 4 — DTOs

```java
// SnapshotPayload.java — what gets serialised into the JSONB column
package com.gitanalytics.analytics.dto;

import java.util.List;

public record SnapshotPayload(
    String username,
    String avatarUrl,
    long commits,
    long prsAuthored,
    long reviewsGiven,
    long linesAdded,
    int currentStreak,
    int longestStreak,
    List<CommitTrendDto> commitTrend,
    String generatedAt
) {}
```

```java
// CreateShareResponse.java
package com.gitanalytics.analytics.dto;

public record CreateShareResponse(String token, String url, String expiresAt) {}
```

#### Step 5 — ShareService

```java
// ShareService.java
package com.gitanalytics.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitanalytics.analytics.dao.AnalyticsDao;
import com.gitanalytics.analytics.dto.*;
import com.gitanalytics.analytics.entity.SharedSnapshot;
import com.gitanalytics.analytics.repository.SharedSnapshotRepository;
import com.gitanalytics.auth.dao.UserDao;
import com.gitanalytics.auth.entity.User;
import com.gitanalytics.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private final SharedSnapshotRepository snapshotRepository;
    private final UserDao userDao;
    private final AnalyticsDao analyticsDao;
    private final AnalyticsService analyticsService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final SecureRandom RANDOM = new SecureRandom();

    public CreateShareResponse createShare(UUID userId) {
        User user = userDao.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime from = now.minusDays(30);

        long commits  = analyticsDao.countCommits(userId, user.getUsername(), from, now);
        long prs      = analyticsDao.countPRsAuthored(userId, user.getUsername(), from, now);
        long reviews  = analyticsDao.countReviewsGiven(userId, user.getUsername(), from, now);
        Object[] lines = analyticsDao.getLinesAddedRemoved(userId, user.getUsername(), from, now);
        long linesAdded = ((Number) lines[0]).longValue();
        StreakDto streak = analyticsService.getStreak(userId, user.getUsername(), "UTC");

        List<CommitTrendDto> trend = analyticsDao.getCommitTrend(userId, user.getUsername(), "daily", from, now);

        SnapshotPayload payload = new SnapshotPayload(
            user.getUsername(),
            null, // avatarUrl not stored; enrich if available
            commits, prs, reviews, linesAdded,
            streak.getCurrentStreak(), streak.getLongestStreak(),
            trend,
            now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        );

        String token = generateToken();
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize snapshot", e);
        }

        SharedSnapshot snap = SharedSnapshot.builder()
            .token(token)
            .user(user)
            .payload(payloadJson)
            .build();
        snapshotRepository.save(snap);

        // Cache the payload in Redis for 30 days to avoid DB hits on public views
        redisTemplate.opsForValue().set("ga:share:" + token, payloadJson, 30, TimeUnit.DAYS);

        return new CreateShareResponse(token, "/share/" + token,
            snap.getExpiresAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    public SnapshotPayload getSnapshot(String token) {
        // Try Redis first
        Object cached = redisTemplate.opsForValue().get("ga:share:" + token);
        if (cached instanceof String json) {
            try {
                return objectMapper.readValue(json, SnapshotPayload.class);
            } catch (Exception ignored) {}
        }

        SharedSnapshot snap = snapshotRepository.findByToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("Snapshot not found"));

        if (snap.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ResourceNotFoundException("Snapshot has expired");
        }

        try {
            SnapshotPayload payload = objectMapper.readValue(snap.getPayload(), SnapshotPayload.class);
            redisTemplate.opsForValue().set("ga:share:" + token, snap.getPayload(), 1, TimeUnit.DAYS);
            return payload;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize snapshot", e);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
```

#### Step 6 — ShareController (authenticated + public endpoints)

```java
// ShareController.java
package com.gitanalytics.analytics.controller;

import com.gitanalytics.analytics.dto.CreateShareResponse;
import com.gitanalytics.analytics.dto.SnapshotPayload;
import com.gitanalytics.analytics.service.ShareService;
import com.gitanalytics.shared.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    /** Authenticated endpoint — creates a new snapshot */
    @PostMapping("/analytics/share")
    public ResponseEntity<ApiResponse<CreateShareResponse>> createShare(
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(shareService.createShare(userId)));
    }

    /** Public endpoint — no auth required */
    @GetMapping("/public/share/{token}")
    public ResponseEntity<ApiResponse<SnapshotPayload>> getSnapshot(
            @PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(shareService.getSnapshot(token)));
    }
}
```

#### Step 7 — Permit public route in SecurityConfig

In `SecurityConfig.java` (or equivalent), add to the `requestMatchers` that are permitted without authentication:

```java
.requestMatchers("/api/v1/public/share/**").permitAll()
```

#### Step 8 — Frontend types

```ts
// In types/index.ts
export interface CreateShareResponse {
  token: string;
  url: string;
  expiresAt: string;
}

export interface SnapshotPayload {
  username: string;
  avatarUrl: string | null;
  commits: number;
  prsAuthored: number;
  reviewsGiven: number;
  linesAdded: number;
  currentStreak: number;
  longestStreak: number;
  commitTrend: CommitTrendPoint[];
  generatedAt: string;
}
```

#### Step 9 — API functions

```ts
// In client.ts
import type { CreateShareResponse, SnapshotPayload } from "@/types";

export const createShare = () =>
  apiClient.post<ApiResponse<CreateShareResponse>>("/analytics/share").then(unwrap);

// Public — no auth cookie sent
export const getSharedSnapshot = (token: string) =>
  apiClient
    .get<ApiResponse<SnapshotPayload>>(`/public/share/${token}`)
    .then(unwrap);
```

#### Step 10 — Share button on dashboard/analytics page

In `frontend/src/app/(app)/analytics/page.tsx`, add a Share button near the page header:

```tsx
import { useState } from "react";
import { Share2, Loader2, Check } from "lucide-react";
import { createShare } from "@/lib/api/client";

// State:
const [sharing, setSharing] = useState(false);
const [shareUrl, setShareUrl] = useState<string | null>(null);

const handleShare = async () => {
  setSharing(true);
  try {
    const result = await createShare();
    const fullUrl = `${window.location.origin}${result.url}`;
    setShareUrl(fullUrl);
    await navigator.clipboard.writeText(fullUrl).catch(() => {});
  } catch {
    // Handle error
  } finally {
    setSharing(false);
  }
};

// JSX (next to page title):
<button
  onClick={handleShare}
  disabled={sharing}
  className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium border border-border rounded-lg hover:bg-muted/40 disabled:opacity-50 transition"
>
  {sharing
    ? <Loader2 className="w-4 h-4 animate-spin" />
    : shareUrl
      ? <Check className="w-4 h-4 text-green-500" />
      : <Share2 className="w-4 h-4" />}
  {sharing ? "Generating…" : shareUrl ? "Link copied!" : "Share"}
</button>

{shareUrl && (
  <p className="text-xs text-muted-foreground mt-2">
    Shareable link: <a href={shareUrl} target="_blank" className="text-primary underline">{shareUrl}</a>
    {" "}· expires in 30 days
  </p>
)}
```

#### Step 11 — Public share page

```tsx
// frontend/src/app/(public)/share/[token]/page.tsx
import { getSharedSnapshot } from "@/lib/api/client";
import type { SnapshotPayload } from "@/types";
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";
import { format } from "date-fns";

export default async function SharedSnapshotPage({
  params,
}: {
  params: { token: string };
}) {
  let payload: SnapshotPayload | null = null;
  let error: string | null = null;

  try {
    payload = await getSharedSnapshot(params.token);
  } catch {
    error = "This snapshot has expired or does not exist.";
  }

  if (error || !payload) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center space-y-2">
          <h1 className="text-xl font-semibold">Snapshot not found</h1>
          <p className="text-muted-foreground text-sm">{error}</p>
        </div>
      </div>
    );
  }

  const chartData = payload.commitTrend.map((pt) => ({
    date: format(new Date(pt.date), "MMM d"),
    commits: pt.count,
  }));

  return (
    <div className="max-w-2xl mx-auto py-16 px-6 space-y-8">
      <div className="text-center space-y-1">
        <h1 className="text-3xl font-bold">{payload.username}</h1>
        <p className="text-muted-foreground text-sm">
          30-day snapshot · Generated {format(new Date(payload.generatedAt), "PPP")}
        </p>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        {[
          { label: "Commits",       value: payload.commits.toLocaleString() },
          { label: "PRs Authored",  value: payload.prsAuthored.toLocaleString() },
          { label: "Reviews Given", value: payload.reviewsGiven.toLocaleString() },
          { label: "Lines Added",   value: payload.linesAdded.toLocaleString() },
        ].map(({ label, value }) => (
          <div key={label} className="bg-card border border-border rounded-xl p-4 text-center">
            <div className="text-xs text-muted-foreground uppercase tracking-wide mb-1">{label}</div>
            <div className="text-xl font-bold">{value}</div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="bg-card border border-border rounded-xl p-4 text-center">
          <div className="text-xs text-muted-foreground uppercase tracking-wide mb-1">Current Streak</div>
          <div className="text-2xl font-bold">{payload.currentStreak}<span className="text-sm font-normal ml-1">days</span></div>
        </div>
        <div className="bg-card border border-border rounded-xl p-4 text-center">
          <div className="text-xs text-muted-foreground uppercase tracking-wide mb-1">Longest Streak</div>
          <div className="text-2xl font-bold">{payload.longestStreak}<span className="text-sm font-normal ml-1">days</span></div>
        </div>
      </div>

      {chartData.length > 0 && (
        <div className="bg-card border border-border rounded-xl p-6">
          <h2 className="font-semibold mb-4 text-sm">Commit Activity</h2>
          <ResponsiveContainer width="100%" height={160}>
            <AreaChart data={chartData} margin={{ top: 4, right: 4, left: -28, bottom: 0 }}>
              <defs>
                <linearGradient id="share-grad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="hsl(var(--primary))" stopOpacity={0.25} />
                  <stop offset="95%" stopColor="hsl(var(--primary))" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
              <XAxis dataKey="date" tick={{ fontSize: 10 }} tickLine={false} axisLine={false} interval="preserveStartEnd" />
              <YAxis tick={{ fontSize: 10 }} tickLine={false} axisLine={false} allowDecimals={false} />
              <Tooltip
                contentStyle={{ background: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: 8, fontSize: 12 }}
                formatter={(v: number) => [v, "commits"]}
              />
              <Area type="monotone" dataKey="commits" stroke="hsl(var(--primary))" strokeWidth={2} fill="url(#share-grad)" dot={false} />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}

      <p className="text-center text-xs text-muted-foreground">
        Powered by <a href="/" className="text-primary underline">GitHub Analytics</a>
      </p>
    </div>
  );
}
```

---

## Migration summary

| Migration | File | What it adds |
|-----------|------|-------------|
| V6 | `V6__repo_stats_snapshots.sql` | `repo_stats_snapshots` table |
| V7 | `V7__issues.sql` | `issues` table |
| V8 | `V8__repo_languages.sql` | `repo_languages` table |
| V9 | `V9__shared_snapshots.sql` | `shared_snapshots` table |

## Feature implementation order summary

| # | Feature | Type | New tables | New endpoints |
|---|---------|------|-----------|--------------|
| 14 | Contributor Network Graph | Frontend-only | — | — |
| 9  | Stars & Forks Growth | Backend + Frontend | `repo_stats_snapshots` | `GET /analytics/repos/{id}/stars-forks-trend` |
| 8  | Release Trend Chart | Backend + Frontend | — (uses existing `releases`) | `GET /analytics/repos/{id}/release-trend` |
| 7  | Issues Sync & Analytics | Backend + Frontend | `issues` | `GET /analytics/repos/{id}/issues` |
| 12 | Language Bytes Upgrade | Backend + Frontend | `repo_languages` | `GET /analytics/language-bytes` |
| 11 | Compare Two Repos | Backend + Frontend | — | `GET /analytics/repos/compare` |
| 10 | Compare Two Contributors | Backend + Frontend | — | `GET /analytics/contributor/compare` |
| 13 | Public Shareable Snapshots | Backend + Frontend | `shared_snapshots` | `POST /analytics/share`, `GET /public/share/{token}` |
