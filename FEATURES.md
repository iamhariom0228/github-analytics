# GitHub Analytics — Free Features Roadmap

Everything here is implementable at **zero cost** using:
- GitHub REST API (5,000 req/hr per user token — free)
- Existing stack: Spring Boot · PostgreSQL (Supabase free) · Redis (Upstash free) · Kafka (Upstash free) · Next.js (Vercel free)
- Browser-native APIs for exports
- Free-tier AI APIs (Groq — 14,400 req/day free, no card required)

Legend: ✅ Already built · 🔲 Not yet built

---

## Category 1 — Data Collection

| Feature | Cost | Status |
|---|---|---|
| Sync commits, PRs, reviews per tracked repo | GitHub API | ✅ |
| Pagination for all GitHub API calls | GitHub API | ✅ |
| Rate limit tracking via X-RateLimit-Remaining header | GitHub API | ✅ |
| Webhook real-time updates (push, PR, review events) | GitHub API | ✅ |
| Incremental sync every 6 hours (fallback) | GitHub API | ✅ |
| Sync public repo stats without tracking (stars, forks, watchers, language breakdown, open issues count) | GitHub API | 🔲 |
| Fetch releases per repo (`/repos/{owner}/{repo}/releases`) | GitHub API | 🔲 |
| Fetch issues per repo (`/repos/{owner}/{repo}/issues`) | GitHub API | 🔲 |
| Fetch contributor stats (`/repos/{owner}/{repo}/stats/contributors`) | GitHub API | 🔲 |
| Search any public repo by URL or `owner/name` without tracking | GitHub API | 🔲 |

---

## Category 2 — Core Metrics (New)

These all derive from GitHub API data — no extra infra needed.

| Feature | Notes |
|---|---|
| Stars / forks / watchers count per repo | One-time fetch on sync |
| Language distribution per repo (% breakdown) | GitHub API returns language byte counts |
| Open vs closed issues count | Issues API |
| Issue average close time | `created_at` → `closed_at` diff |
| Release frequency (releases per month) | Releases API |
| Code churn per contributor (additions − deletions net) | Already storing additions/deletions |
| PR merge rate (merged / total PRs %) | Derived from existing PR data |
| Reviewer coverage (% of PRs with at least one review) | Derived from existing data |

---

## Category 3 — Analytics Enhancements

Already have: heatmap, PR lifecycle, size distribution, reviews, streak, leaderboard, bus factor, stale PRs.

| Feature | Notes |
|---|---|
| Commit trend chart (daily/weekly line chart) | `/analytics/commits/trend` — data query + Recharts area chart |
| `/analytics/overview` summary card (commits, PRs authored, reviews given) | Single aggregation endpoint |
| Language distribution donut chart per repo | Store language bytes from GitHub API |
| Stars/forks growth over time (if repo is tracked long-term) | Snapshot table with daily cron |
| Issue analytics tab (open/closed, avg close time) | New DB table + GitHub issues sync |
| Release trend chart | New DB table + releases sync |
| Contributor network graph (who reviews whose PRs most) | D3 force graph — no extra data needed, use pr_reviews table |
| Code churn leaderboard (net lines added − deleted) | Derived from existing commits table |
| PR merge rate over time | Derived from existing data |
| Compare two repos side-by-side | Two sets of existing analytics queries |
| Compare two contributors side-by-side | Two sets of existing analytics queries |

---

## Category 4 — Repo Health Score

A single computed score (0–100) based on weighted signals — all from existing/free data.

| Signal | Weight | Source |
|---|---|---|
| Commit frequency (last 30 days > 0) | 20 | commits table |
| PR review coverage (% of PRs reviewed) | 20 | pr_reviews table |
| Bus factor > 1 contributor | 15 | commits table |
| Average PR merge time < 48 hrs | 15 | pull_requests table |
| No stale PRs older than 14 days | 15 | pull_requests table |
| Has recent release (last 90 days) | 15 | releases table (new) |

Score + label (Healthy / At Risk / Needs Attention) + breakdown card in UI.

---

## Category 5 — AI-Generated Summaries (Free Tier)

**Groq API** — free tier, no credit card, 14,400 req/day, Llama 3.1 70B model.

| Feature | Notes |
|---|---|
| Weekly digest AI insight ("Your most productive day was Monday, 3× your usual output") | Generate 1 sentence per insight in DigestService |
| Repo health AI summary ("This repo shows declining activity — last commit was 45 days ago, no open PRs") | Call Groq with repo stats JSON → 2-3 sentence summary |
| Personal productivity summary on dashboard | Small card: "Based on your patterns, you code best Tuesday mornings" |

Implementation: `GroqApiClient.java` (simple REST call) → called from `AnalyticsService` and `DigestService`.

---

## Category 6 — UI / UX Improvements

All free — pure frontend work.

| Feature | Notes |
|---|---|
| Dark mode toggle | Tailwind `dark:` classes + next-themes |
| Loading skeletons on all data cards | shadcn/ui Skeleton |
| Empty states with helpful copy ("No commits in this period — try a wider date range") | Conditional render |
| Commit trend chart (area/line) on analytics page | Recharts AreaChart |
| Contributor network graph | D3.js or react-force-graph (free) |
| Repo health score card on repos page | Computed from existing data |
| Export data as CSV | `papaparse` + browser download — no server |
| Export analytics as PDF | Browser `window.print()` with print stylesheet |
| Search / filter on leaderboard by contributor name | Frontend filter, no API change |
| Keyboard shortcuts (press `D` → dashboard, `R` → repos, etc.) | Frontend only |

---

## Category 7 — Public Repo Lookup (No Account Needed)

Allow any visitor to enter a GitHub repo URL and see a read-only analytics snapshot.
No auth required — use GitHub API unauthenticated (60 req/hr) or with a server-side token.

| Feature | Notes |
|---|---|
| `/explore?repo=owner/name` public page | Server-side GitHub API fetch, cached in Redis 1hr |
| Shows: stars, forks, language breakdown, top contributors, recent commit activity, PR merge rate | All from GitHub API |
| Repo health score | Computed on the fly |
| Share link for a repo snapshot | Shareable URL, cached result |

This is one of the highest-impact features for portfolio — lets anyone use the app without signing in.

---

## Category 8 — Polish / Portfolio Quality

| Feature | Notes |
|---|---|
| Demo seed script (generates synthetic user + 3 repos with 200 commits, 50 PRs) | SQL seed or Java `DataSeeder` |
| `/actuator/health` endpoint | ✅ Already configured |
| Rate limiting (100 req/min per user) | Already exists via RateLimitFilter |
| Postman / Bruno collection for all endpoints | Export from existing routes |
| Architecture diagram (Excalidraw) | One-time work |
| README with screenshots, live demo, setup guide | Markdown |

---

## Suggested Implementation Order

1. **Commit trend chart** — highest visibility, data already exists
2. **Dark mode** — immediate visual impact, 1–2 hours
3. **Loading skeletons + empty states** — makes app feel production-ready
4. **`/analytics/overview`** — fills the gap between dashboard and deep analytics
5. **Public repo lookup (`/explore`)** — anyone can use the app without logging in, great for demos
6. **Repo health score** — memorable, shareable, impressive on a demo
7. **Language distribution** — sync language data, add donut chart
8. **AI summary via Groq** — 3-5 hours, very high resume impact
9. **CSV export** — quick win, practical feature
10. **Contributor network graph** — visually impressive, D3 or react-force-graph
11. **Issues + releases sync** — expands data model significantly
12. **Compare repos / contributors** — advanced analytical feature
