# Tier A — Implementation Plan

All 6 features use data that already exists in the database.
No new tables, no new GitHub API calls, no new infrastructure.

---

## Feature 1 — Code Churn Leaderboard

**What it does:** Ranks contributors by total lines changed (`additions + deletions`)
over a selected date range. Tells the team who is writing/touching the most code.

**Already in place:**
- `ContributorStatsDto` already has `linesAdded` and `linesRemoved` fields
- `getTeamLeaderboard` query already returns these columns
- Leaderboard table on `/team` page already renders them

**Gap:** No dedicated churn sort/endpoint, and the Team page doesn't have a churn toggle.

---

### Step 1 — Backend: Add churn sort to `AnalyticsRepository`

**File:** `backend/src/main/java/com/gitanalytics/analytics/repository/AnalyticsRepository.java`

Add a new query method that orders by total lines changed instead of commit count:

```java
@Query(nativeQuery = true, value = """
    SELECT c.author_login,
           COUNT(*) AS commits,
           CAST(SUM(c.additions) AS bigint) AS lines_added,
           CAST(SUM(c.deletions) AS bigint) AS lines_removed
    FROM commits c
    JOIN tracked_repos r ON c.repo_id = r.id
    WHERE r.user_id = :userId AND r.id = :repoId
      AND c.committed_at BETWEEN :from AND :to
    GROUP BY c.author_login
    ORDER BY (SUM(c.additions) + SUM(c.deletions)) DESC
    LIMIT 20
    """)
List<Object[]> getChurnLeaderboard(
    @Param("userId") UUID userId,
    @Param("repoId") UUID repoId,
    @Param("from") OffsetDateTime from,
    @Param("to") OffsetDateTime to);
```

---

### Step 2 — Backend: Add to `AnalyticsDao` interface

**File:** `backend/src/main/java/com/gitanalytics/analytics/dao/AnalyticsDao.java`

```java
// Churn leaderboard — ranked by total lines changed
List<ContributorStatsDto> getChurnLeaderboard(UUID userId, UUID repoId,
                                               OffsetDateTime from, OffsetDateTime to);
```

---

### Step 3 — Backend: Implement in `AnalyticsDaoImpl`

**File:** `backend/src/main/java/com/gitanalytics/analytics/dao/impl/AnalyticsDaoImpl.java`

```java
@Override
public List<ContributorStatsDto> getChurnLeaderboard(UUID userId, UUID repoId,
                                                      OffsetDateTime from, OffsetDateTime to) {
    return analyticsRepository.getChurnLeaderboard(userId, repoId, from, to).stream()
            .map(r -> new ContributorStatsDto(
                    (String) r[0],
                    ((Number) r[1]).longValue(),
                    r[2] != null ? ((Number) r[2]).longValue() : 0,
                    r[3] != null ? ((Number) r[3]).longValue() : 0))
            .toList();
}
```

---

### Step 4 — Backend: Add service method

**File:** `backend/src/main/java/com/gitanalytics/analytics/service/AnalyticsService.java`

```java
// ── Churn Leaderboard ─────────────────────────────────────────────────────

public List<ContributorStatsDto> getChurnLeaderboard(UUID userId, UUID repoId,
                                                      OffsetDateTime from, OffsetDateTime to) {
    return analyticsDao.getChurnLeaderboard(userId, repoId, from, to);
}
```

---

### Step 5 — Backend: Add controller endpoint

**File:** `backend/src/main/java/com/gitanalytics/analytics/controller/AnalyticsController.java`

```java
@GetMapping("/team/churn")
public ResponseEntity<ApiResponse<List<ContributorStatsDto>>> getChurnLeaderboard(
        @AuthenticationPrincipal UserDetails principal,
        @RequestParam UUID repoId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
    UUID userId = UUID.fromString(principal.getUsername());
    return ResponseEntity.ok(ApiResponse.ok(analyticsService.getChurnLeaderboard(userId, repoId, from, to)));
}
```

---

### Step 6 — Frontend: Add API function

**File:** `frontend/src/lib/api/client.ts`

```ts
export const getChurnLeaderboard = (repoId: string, from: string, to: string) =>
  apiClient
    .get<ApiResponse<ContributorStats[]>>("/analytics/team/churn", {
      params: { repoId, from, to },
    })
    .then(unwrap);
```

---

### Step 7 — Frontend: Add hook

**File:** `frontend/src/hooks/useAnalytics.ts`

```ts
export function useChurnLeaderboard(repoId: string, from: string, to: string) {
  return useQuery({
    queryKey: ["churn-leaderboard", repoId, from, to],
    queryFn: () => getChurnLeaderboard(repoId, from, to),
    enabled: !!repoId,
    staleTime: ANALYTICS_STALE_MS,
  });
}
```

---

### Step 8 — Frontend: Update Team page

**File:** `frontend/src/app/(app)/team/page.tsx`

Add a sort toggle ("By Commits" | "By Churn") above the leaderboard table.
When "By Churn" is selected, use `useChurnLeaderboard` instead of `useLeaderboard`.
Add a "Net churn" column (additions + deletions) to the table.

```tsx
const [sortMode, setSortMode] = useState<"commits" | "churn">("commits");

const { data: leaderboard } = useLeaderboard(repoId, from, to);
const { data: churn }       = useChurnLeaderboard(repoId, from, to);

const tableData = sortMode === "commits" ? leaderboard : churn;
```

Toggle buttons (same style as date-range preset buttons already on the page):
```tsx
<div className="flex gap-2">
  {(["commits", "churn"] as const).map((m) => (
    <button key={m} onClick={() => setSortMode(m)}
      className={sortMode === m ? "...active styles..." : "...inactive styles..."}>
      {m === "commits" ? "By Commits" : "By Churn"}
    </button>
  ))}
</div>
```

---

## Feature 2 — PR Merge Rate Over Time

**What it does:** A line chart showing `merged PRs / total PRs` as a percentage,
week by week. Teams use this to track whether review bottlenecks are improving.

**Already in place:**
- `pull_requests` table has `merged_at`, `state`, `created_at`
- Trend pattern established by `getCommitTrend`

---

### Step 1 — Backend: New DTO

**New file:** `backend/src/main/java/com/gitanalytics/analytics/dto/PRMergeRateDto.java`

```java
package com.gitanalytics.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PRMergeRateDto {
    private String week;      // ISO date of the week start
    private long total;
    private long merged;
    private double mergeRate; // 0.0–100.0
}
```

---

### Step 2 — Backend: New query in `AnalyticsRepository`

**File:** `backend/src/main/java/com/gitanalytics/analytics/repository/AnalyticsRepository.java`

```java
@Query(nativeQuery = true, value = """
    SELECT
      TO_CHAR(DATE_TRUNC('week', pr.created_at), 'YYYY-MM-DD') AS week,
      COUNT(*) AS total,
      COUNT(*) FILTER (WHERE pr.merged_at IS NOT NULL) AS merged
    FROM pull_requests pr
    JOIN tracked_repos r ON pr.repo_id = r.id
    WHERE r.user_id = :userId
      AND (pr.author_login = :login OR (pr.author_login IS NULL AND r.owner = :login))
      AND pr.created_at BETWEEN :from AND :to
    GROUP BY DATE_TRUNC('week', pr.created_at)
    ORDER BY DATE_TRUNC('week', pr.created_at) ASC
    """)
List<Object[]> getPRMergeRateTrend(
    @Param("userId") UUID userId,
    @Param("login") String login,
    @Param("from") OffsetDateTime from,
    @Param("to") OffsetDateTime to);
```

---

### Step 3 — Backend: Add to `AnalyticsDao` interface

**File:** `backend/src/main/java/com/gitanalytics/analytics/dao/AnalyticsDao.java`

```java
List<PRMergeRateDto> getPRMergeRateTrend(UUID userId, String login,
                                          OffsetDateTime from, OffsetDateTime to);
```

---

### Step 4 — Backend: Implement in `AnalyticsDaoImpl`

**File:** `backend/src/main/java/com/gitanalytics/analytics/dao/impl/AnalyticsDaoImpl.java`

```java
@Override
public List<PRMergeRateDto> getPRMergeRateTrend(UUID userId, String login,
                                                  OffsetDateTime from, OffsetDateTime to) {
    return analyticsRepository.getPRMergeRateTrend(userId, login, from, to).stream()
            .map(r -> {
                long total  = ((Number) r[1]).longValue();
                long merged = ((Number) r[2]).longValue();
                double rate = total > 0 ? (double) merged / total * 100 : 0;
                return new PRMergeRateDto((String) r[0], total, merged, rate);
            })
            .toList();
}
```

---

### Step 5 — Backend: Add service method

**File:** `backend/src/main/java/com/gitanalytics/analytics/service/AnalyticsService.java`

```java
// ── PR Merge Rate Trend ───────────────────────────────────────────────────

public List<PRMergeRateDto> getPRMergeRateTrend(UUID userId, String login,
                                                  OffsetDateTime from, OffsetDateTime to) {
    return analyticsDao.getPRMergeRateTrend(userId, login, from, to);
}
```

---

### Step 6 — Backend: Add controller endpoint

**File:** `backend/src/main/java/com/gitanalytics/analytics/controller/AnalyticsController.java`

```java
@GetMapping("/prs/merge-rate")
public ResponseEntity<ApiResponse<List<PRMergeRateDto>>> getPRMergeRate(
        @AuthenticationPrincipal UserDetails principal,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
    UUID userId = UUID.fromString(principal.getUsername());
    String login = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found")).getUsername();
    return ResponseEntity.ok(ApiResponse.ok(analyticsService.getPRMergeRateTrend(userId, login, from, to)));
}
```

---

### Step 7 — Frontend: Add type

**File:** `frontend/src/types/index.ts`

```ts
export interface PRMergeRatePoint {
  week: string;
  total: number;
  merged: number;
  mergeRate: number;
}
```

---

### Step 8 — Frontend: Add API function and hook

**File:** `frontend/src/lib/api/client.ts`

```ts
export const getPRMergeRate = (from: string, to: string) =>
  apiClient
    .get<ApiResponse<PRMergeRatePoint[]>>("/analytics/prs/merge-rate", { params: { from, to } })
    .then(unwrap);
```

**File:** `frontend/src/hooks/useAnalytics.ts`

```ts
export function usePRMergeRate(from: string, to: string) {
  return useQuery({
    queryKey: ["pr-merge-rate", from, to],
    queryFn: () => getPRMergeRate(from, to),
    staleTime: ANALYTICS_STALE_MS,
  });
}
```

---

### Step 9 — Frontend: New chart component

**New file:** `frontend/src/app/(app)/analytics/_components/PRMergeRateChart.tsx`

```tsx
"use client";
import {
  ComposedChart, Bar, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, Legend,
} from "recharts";
import type { PRMergeRatePoint } from "@/types";

interface Props {
  data: PRMergeRatePoint[];
  isLoading: boolean;
}

// Dual-axis chart: bars = total PRs, line = merge rate %
export function PRMergeRateChart({ data, isLoading }: Props) {
  if (isLoading) return <div className="h-64 animate-pulse bg-muted rounded-xl" />;
  if (!data.length)
    return (
      <div className="h-64 flex items-center justify-center text-sm text-muted-foreground">
        No PR data for this period.
      </div>
    );

  const formatted = data.map((d) => ({
    ...d,
    week: new Date(d.week).toLocaleDateString("en-US", { month: "short", day: "numeric" }),
    mergeRate: Math.round(d.mergeRate),
  }));

  return (
    <ResponsiveContainer width="100%" height={256}>
      <ComposedChart data={formatted} margin={{ top: 8, right: 16, left: -20, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
        <XAxis dataKey="week" tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} />
        <YAxis yAxisId="left" tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} allowDecimals={false} />
        <YAxis yAxisId="right" orientation="right" domain={[0, 100]} unit="%" tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} />
        <Tooltip
          contentStyle={{ background: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: "8px", fontSize: "12px" }}
          formatter={(value: number, name: string) =>
            name === "Merge Rate" ? [`${value}%`, name] : [value, name]
          }
        />
        <Legend wrapperStyle={{ fontSize: "12px" }} />
        <Bar yAxisId="left" dataKey="total" name="Total PRs" fill="hsl(var(--primary) / 0.2)" radius={[4, 4, 0, 0]} />
        <Bar yAxisId="left" dataKey="merged" name="Merged PRs" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
        <Line yAxisId="right" type="monotone" dataKey="mergeRate" name="Merge Rate" stroke="#10b981" strokeWidth={2} dot={false} />
      </ComposedChart>
    </ResponsiveContainer>
  );
}
```

---

### Step 10 — Frontend: Add to Analytics page Pull Requests tab

**File:** `frontend/src/app/(app)/analytics/page.tsx`

Import `usePRMergeRate` and `PRMergeRateChart`, add inside the "Pull Requests" tab:

```tsx
const { data: mergeRate, isLoading: mergeRateLoading } = usePRMergeRate(from, to);

// In "Pull Requests" tab:
<div className="bg-card border border-border rounded-xl p-6">
  <h2 className="font-semibold mb-1">PR Merge Rate Over Time</h2>
  <p className="text-xs text-muted-foreground mb-5">
    Weekly breakdown of total PRs opened vs merged, with merge rate percentage.
  </p>
  <PRMergeRateChart data={mergeRate ?? []} isLoading={mergeRateLoading} />
</div>
```

---

## Feature 3 — Reviewer Coverage

**What it does:** Shows what percentage of PRs received at least one review before
being merged or closed. Low coverage = code shipping without review.

**Already in place:**
- `getPRReviewCoverage(repoId, since)` query already exists in `AnalyticsRepository`
  returning `[total_prs, reviewed_prs]`
- Used internally by `getRepoHealth` but never exposed as a standalone endpoint

---

### Step 1 — Backend: New DTO

**New file:** `backend/src/main/java/com/gitanalytics/analytics/dto/ReviewerCoverageDto.java`

```java
package com.gitanalytics.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewerCoverageDto {
    private long totalPRs;
    private long reviewedPRs;
    private double coveragePct;   // 0.0–100.0
    private long unreviewedPRs;
}
```

---

### Step 2 — Backend: Add user-scoped coverage query to `AnalyticsRepository`

**File:** `backend/src/main/java/com/gitanalytics/analytics/repository/AnalyticsRepository.java`

The existing `getPRReviewCoverage` is repo-scoped (no author filter). Add a user-scoped version:

```java
@Query(nativeQuery = true, value = """
    SELECT
      COUNT(DISTINCT pr.id) AS total_prs,
      COUNT(DISTINCT rv.pr_id) AS reviewed_prs
    FROM pull_requests pr
    JOIN tracked_repos r ON pr.repo_id = r.id
    LEFT JOIN pr_reviews rv ON rv.pr_id = pr.id
    WHERE r.user_id = :userId
      AND (pr.author_login = :login OR (pr.author_login IS NULL AND r.owner = :login))
      AND pr.created_at BETWEEN :from AND :to
    """)
Object[] getReviewerCoverageForUser(
    @Param("userId") UUID userId,
    @Param("login") String login,
    @Param("from") OffsetDateTime from,
    @Param("to") OffsetDateTime to);
```

---

### Step 3 — Backend: Add to `AnalyticsDao` interface

**File:** `backend/src/main/java/com/gitanalytics/analytics/dao/AnalyticsDao.java`

```java
Object[] getReviewerCoverageForUser(UUID userId, String login,
                                     OffsetDateTime from, OffsetDateTime to);
```

---

### Step 4 — Backend: Implement in `AnalyticsDaoImpl`

**File:** `backend/src/main/java/com/gitanalytics/analytics/dao/impl/AnalyticsDaoImpl.java`

```java
@Override
public Object[] getReviewerCoverageForUser(UUID userId, String login,
                                            OffsetDateTime from, OffsetDateTime to) {
    return firstRow(analyticsRepository.getReviewerCoverageForUser(userId, login, from, to));
}
```

---

### Step 5 — Backend: Add service method

**File:** `backend/src/main/java/com/gitanalytics/analytics/service/AnalyticsService.java`

```java
// ── Reviewer Coverage ─────────────────────────────────────────────────────

public ReviewerCoverageDto getReviewerCoverage(UUID userId, String login,
                                                OffsetDateTime from, OffsetDateTime to) {
    Object[] row = analyticsDao.getReviewerCoverageForUser(userId, login, from, to);
    long total    = row[0] != null ? ((Number) row[0]).longValue() : 0;
    long reviewed = row[1] != null ? ((Number) row[1]).longValue() : 0;
    double pct    = total > 0 ? (double) reviewed / total * 100 : 0;
    return new ReviewerCoverageDto(total, reviewed, pct, total - reviewed);
}
```

---

### Step 6 — Backend: Add controller endpoint

**File:** `backend/src/main/java/com/gitanalytics/analytics/controller/AnalyticsController.java`

```java
@GetMapping("/prs/reviewer-coverage")
public ResponseEntity<ApiResponse<ReviewerCoverageDto>> getReviewerCoverage(
        @AuthenticationPrincipal UserDetails principal,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
    UUID userId = UUID.fromString(principal.getUsername());
    String login = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found")).getUsername();
    return ResponseEntity.ok(ApiResponse.ok(analyticsService.getReviewerCoverage(userId, login, from, to)));
}
```

---

### Step 7 — Frontend: Add type, API function, hook

**File:** `frontend/src/types/index.ts`

```ts
export interface ReviewerCoverage {
  totalPRs: number;
  reviewedPRs: number;
  coveragePct: number;
  unreviewedPRs: number;
}
```

**File:** `frontend/src/lib/api/client.ts`

```ts
export const getReviewerCoverage = (from: string, to: string) =>
  apiClient
    .get<ApiResponse<ReviewerCoverage>>("/analytics/prs/reviewer-coverage", { params: { from, to } })
    .then(unwrap);
```

**File:** `frontend/src/hooks/useAnalytics.ts`

```ts
export function useReviewerCoverage(from: string, to: string) {
  return useQuery({
    queryKey: ["reviewer-coverage", from, to],
    queryFn: () => getReviewerCoverage(from, to),
    staleTime: ANALYTICS_STALE_MS,
  });
}
```

---

### Step 8 — Frontend: Add coverage card to Analytics page Reviews tab

**File:** `frontend/src/app/(app)/analytics/page.tsx`

Add inside the "Reviews" tab, above the existing `ReviewsSection`:

```tsx
const { data: coverage, isLoading: coverageLoading } = useReviewerCoverage(from, to);

// Coverage stat card
<div className="bg-card border border-border rounded-xl p-6">
  <h2 className="font-semibold mb-1">Reviewer Coverage</h2>
  <p className="text-xs text-muted-foreground mb-4">
    Percentage of your PRs that received at least one review.
  </p>
  {coverageLoading ? (
    <div className="h-20 animate-pulse bg-muted rounded-xl" />
  ) : (
    <div className="flex items-center gap-8">
      <div className="text-center">
        <div className="text-3xl font-bold text-primary">
          {Math.round(coverage?.coveragePct ?? 0)}%
        </div>
        <div className="text-xs text-muted-foreground mt-1">Coverage</div>
      </div>
      <div className="flex-1">
        <div className="h-3 bg-muted rounded-full overflow-hidden">
          <div
            className="h-full bg-primary rounded-full transition-all duration-700"
            style={{ width: `${coverage?.coveragePct ?? 0}%` }}
          />
        </div>
        <div className="flex justify-between text-xs text-muted-foreground mt-1.5">
          <span>{coverage?.reviewedPRs ?? 0} reviewed</span>
          <span>{coverage?.unreviewedPRs ?? 0} unreviewed</span>
        </div>
      </div>
    </div>
  )}
</div>
```

---

## Feature 4 — Keyboard Shortcuts

**What it does:** Navigate the app without touching the mouse.
`d` → Dashboard, `r` → Repos, `a` → Analytics, `t` → Team, `f` → Feed,
`g` → Goals, `e` → Explore, `?` → show cheatsheet overlay.

**Already in place:**
- `frontend/src/hooks/useKeyboardShortcuts.ts` exists with `d`, `r`, `a`, `t`, `s`, `e` bound
- Hook is already called inside `Sidebar.tsx`

**Gap:** Missing shortcuts for new Tier 3 pages (`f`, `g`, `q` for review-queue, `c` for compare),
and no visible cheatsheet overlay so users don't know shortcuts exist.

---

### Step 1 — Update `useKeyboardShortcuts.ts`

**File:** `frontend/src/hooks/useKeyboardShortcuts.ts`

Add missing routes and `?` for the cheatsheet. Export the shortcut map so the
overlay can render it dynamically.

```ts
import { useEffect } from "react";
import { useRouter } from "next/navigation";

export const SHORTCUTS: Record<string, { label: string; path: string }> = {
  d: { label: "Dashboard",    path: "/dashboard" },
  r: { label: "Repositories", path: "/repos" },
  a: { label: "Analytics",    path: "/analytics" },
  t: { label: "Team",         path: "/team" },
  f: { label: "Activity Feed",path: "/feed" },
  g: { label: "Goals",        path: "/goals" },
  q: { label: "Review Queue", path: "/review-queue" },
  c: { label: "Compare",      path: "/compare" },
  e: { label: "Explore",      path: "/explore" },
  s: { label: "Settings",     path: "/settings" },
};

export function useKeyboardShortcuts(onCheatsheet?: () => void) {
  const router = useRouter();

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      // Ignore when typing in inputs / textareas
      const tag = (e.target as HTMLElement).tagName;
      if (["INPUT", "TEXTAREA", "SELECT"].includes(tag)) return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;

      if (e.key === "?") {
        onCheatsheet?.();
        return;
      }

      const shortcut = SHORTCUTS[e.key.toLowerCase()];
      if (shortcut) router.push(shortcut.path);
    };

    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [router, onCheatsheet]);
}
```

---

### Step 2 — Add `ShortcutCheatsheet` component

**New file:** `frontend/src/components/shared/ShortcutCheatsheet.tsx`

```tsx
"use client";

import { SHORTCUTS } from "@/hooks/useKeyboardShortcuts";
import { X } from "lucide-react";

interface Props {
  open: boolean;
  onClose: () => void;
}

export function ShortcutCheatsheet({ open, onClose }: Props) {
  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="bg-card border border-border rounded-2xl p-6 w-80 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-semibold">Keyboard Shortcuts</h2>
          <button onClick={onClose} className="text-muted-foreground hover:text-foreground">
            <X className="w-4 h-4" />
          </button>
        </div>
        <div className="space-y-2">
          {Object.entries(SHORTCUTS).map(([key, { label }]) => (
            <div key={key} className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">{label}</span>
              <kbd className="px-2 py-0.5 text-xs font-mono bg-muted border border-border rounded">
                {key}
              </kbd>
            </div>
          ))}
          <div className="flex items-center justify-between text-sm pt-1 border-t border-border mt-3">
            <span className="text-muted-foreground">Show this panel</span>
            <kbd className="px-2 py-0.5 text-xs font-mono bg-muted border border-border rounded">?</kbd>
          </div>
        </div>
      </div>
    </div>
  );
}
```

---

### Step 3 — Wire cheatsheet into Sidebar

**File:** `frontend/src/components/shared/Sidebar.tsx`

```tsx
import { useState } from "react";
import { ShortcutCheatsheet } from "./ShortcutCheatsheet";

// Inside Sidebar component:
const [showCheatsheet, setShowCheatsheet] = useState(false);
useKeyboardShortcuts(() => setShowCheatsheet(true));

// In the bottom bar (next to Logout):
<button
  onClick={() => setShowCheatsheet(true)}
  className="flex items-center gap-3 px-3 py-2 rounded-md text-xs text-muted-foreground hover:bg-accent hover:text-foreground transition-colors"
  title="Keyboard shortcuts (?)"
>
  <Keyboard className="w-4 h-4" />
  Shortcuts
</button>

<ShortcutCheatsheet open={showCheatsheet} onClose={() => setShowCheatsheet(false)} />
```

Import `Keyboard` from `lucide-react` (add to existing import line).

---

## Feature 5 — PDF / Print Export

**What it does:** A single "Export PDF" button on the Analytics page that opens
the browser print dialog with a clean, print-optimised layout (no sidebar, no nav,
white background, full-width charts).

**No backend changes needed.**

---

### Step 1 — Add print stylesheet

**New file:** `frontend/src/app/(app)/analytics/print.css`

```css
@media print {
  /* Hide navigation and non-content elements */
  aside,
  nav,
  [data-no-print],
  button:not([data-print-keep]) {
    display: none !important;
  }

  /* Full-width layout */
  body, main, .print-container {
    width: 100% !important;
    max-width: 100% !important;
    margin: 0 !important;
    padding: 0 !important;
    background: white !important;
    color: black !important;
  }

  /* Force charts to be visible */
  .recharts-surface {
    page-break-inside: avoid;
  }

  /* Page breaks */
  .print-break-before {
    page-break-before: always;
  }

  /* Ensure text is readable */
  * {
    -webkit-print-color-adjust: exact;
    color-adjust: exact;
  }
}
```

---

### Step 2 — Import print stylesheet in Analytics page

**File:** `frontend/src/app/(app)/analytics/page.tsx`

```tsx
import "./print.css";
```

---

### Step 3 — Add export button to Analytics page

**File:** `frontend/src/app/(app)/analytics/page.tsx`

Add next to the `DateRangePicker` in the page header:

```tsx
import { Printer } from "lucide-react";

// In the header row:
<div className="flex items-center gap-3">
  <DateRangePicker value={preset} onChange={(p) => setPreset(p)} />
  <button
    data-print-keep
    onClick={() => window.print()}
    className="flex items-center gap-2 px-3 py-2 text-sm border border-border rounded-lg hover:bg-muted/60 transition-colors"
  >
    <Printer className="w-4 h-4" />
    Export PDF
  </button>
</div>
```

---

### Step 4 — Add `data-no-print` to interactive controls

In the Analytics page, add `data-no-print` to the tab bar, granularity toggles,
and the date picker so only the charts and stat cards appear in the PDF.

```tsx
<div className="flex border-b border-border" data-no-print>
  {/* tab buttons */}
</div>
```

---

## Feature 6 — Demo Seed Script

**What it does:** A "Try Demo" button on the login page that logs the visitor in as
a pre-seeded demo user with 90 days of realistic commit, PR, and review data —
no GitHub account required.

**Already in place:**
- `SeedDataRunner.java` exists at `@Profile("dev")` and creates:
  - Demo user: username `demo-user`, GitHub ID `999999`
  - 3 tracked repos with 90 days of commits, 30 PRs, and 30 reviews from 4 teammates
  - Dev login endpoint: `GET /api/v1/dev/demo-login`

**Gap:** The login page has no "Try Demo" button pointing to this endpoint.

---

### Step 1 — Add demo login function to API client

**File:** `frontend/src/lib/api/client.ts`

```ts
export const demoLogin = () =>
  apiClient.get<ApiResponse<void>>("/dev/demo-login").then(() => {
    window.location.href = "/dashboard";
  });
```

---

### Step 2 — Add "Try Demo" button to the login page

**File:** `frontend/src/app/(public)/page.tsx` (or wherever the login/landing page lives)

```tsx
import { demoLogin } from "@/lib/api/client";
import { useState } from "react";

// Add below the "Login with GitHub" button:
const [demoLoading, setDemoLoading] = useState(false);

<div className="relative my-4">
  <div className="absolute inset-0 flex items-center">
    <div className="w-full border-t border-border" />
  </div>
  <div className="relative flex justify-center text-xs text-muted-foreground">
    <span className="bg-background px-2">or</span>
  </div>
</div>

<button
  onClick={async () => {
    setDemoLoading(true);
    await demoLogin().catch(() => setDemoLoading(false));
  }}
  disabled={demoLoading}
  className="w-full flex items-center justify-center gap-2 px-4 py-2.5 border border-border rounded-lg text-sm hover:bg-muted/50 disabled:opacity-50 transition-colors"
>
  {demoLoading ? (
    <span className="animate-spin w-4 h-4 border-2 border-current border-t-transparent rounded-full" />
  ) : (
    <span>🚀</span>
  )}
  Try Demo — no account needed
</button>

<p className="text-xs text-muted-foreground text-center mt-2">
  Pre-loaded with 90 days of realistic GitHub data
</p>
```

---

### Step 3 — Gate the demo button on dev profile only (optional)

To prevent the demo button from appearing in production, add an environment check:

**File:** `frontend/src/app/(public)/page.tsx`

```tsx
const isDemoEnabled = process.env.NEXT_PUBLIC_DEMO_ENABLED === "true";

{isDemoEnabled && (
  // ... demo button JSX
)}
```

**File:** `frontend/.env.local`

```
NEXT_PUBLIC_DEMO_ENABLED=true
```

**File:** `frontend/.env.production`

```
NEXT_PUBLIC_DEMO_ENABLED=false
```

---

## Build Order

Implement in this sequence to keep each PR small and self-contained:

| Order | Feature | Why this order |
|-------|---------|---------------|
| 1 | Keyboard Shortcuts | Frontend-only, no risk, done in 1 hour |
| 2 | Demo Seed Script | Frontend-only (backend already done), unblocks testing everything else |
| 3 | PDF Export | Frontend-only, 1 hour |
| 4 | Reviewer Coverage | 1 new DTO + 1 query + 1 stat card |
| 5 | Code Churn Leaderboard | Extends existing leaderboard, low risk |
| 6 | PR Merge Rate | Most complex chart, build last when patterns are fresh |

Total estimated effort: **2–3 focused days** for all 6 features.
