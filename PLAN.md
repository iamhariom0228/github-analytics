# GitHub Analytics — Feature Roadmap & Improvement Plan

Scoring guide:
- **Impact**: How much value does this add for a real developer using the app daily?
- **Effort**: How much backend + frontend work is involved?

Legend: ⭐⭐⭐ = High · ⭐⭐ = Medium · ⭐ = Low

---

## Tier A — High Impact, Low Effort
> Build these first. Data already exists, minimal new infrastructure.

### 1. Code Churn Leaderboard
**Impact:** ⭐⭐⭐ | **Effort:** ⭐

Who writes the most net code? Ranks contributors by `additions − deletions` over a date range.
Helps teams identify high-output contributors and spot developers who delete more than they write (refactoring heroes).

- Backend: one new query on the `commits` table, new endpoint `/analytics/team/churn`
- Frontend: new card on the Team page alongside the existing leaderboard

---

### 2. PR Merge Rate Over Time
**Impact:** ⭐⭐⭐ | **Effort:** ⭐

Line chart showing `merged / total PRs` as a percentage, week by week.
Real-world use: teams track whether review bottlenecks are getting better or worse over sprints.

- Backend: derived from existing `pull_requests` table, no new sync needed
- Frontend: new chart in the Pull Requests tab on the Analytics page

---

### 3. Reviewer Coverage
**Impact:** ⭐⭐⭐ | **Effort:** ⭐

Percentage of PRs that received at least one review before merge.
A key engineering health signal — low coverage means code ships without eyes on it.

- Backend: derived from `pr_reviews` + `pull_requests` join, already stored
- Frontend: single stat card + trend line on the Repo Health page

---

### 4. Keyboard Shortcuts
**Impact:** ⭐⭐ | **Effort:** ⭐

Press `D` → Dashboard, `R` → Repos, `A` → Analytics, `T` → Team, `F` → Feed, `?` → shortcut cheatsheet.
Power users navigate without touching the mouse. Makes the app feel polished and production-grade.

- Frontend only — extend the existing `useKeyboardShortcuts` hook
- No backend changes

---

### 5. PDF / Print Export
**Impact:** ⭐⭐ | **Effort:** ⭐

Export any analytics view as a printable PDF using `window.print()` with a print stylesheet.
Real-world use: developers share weekly reports with managers or include in performance reviews.

- Frontend only — add a print stylesheet and a "Export PDF" button
- No backend changes

---

### 6. Demo Seed Script
**Impact:** ⭐⭐⭐ | **Effort:** ⭐

A Java `DataSeeder` or SQL script that generates a synthetic user + 3 repos with 200 commits,
50 PRs, and 30 reviews. Anyone can try the app without connecting a GitHub account.
Critical for portfolio demos and onboarding new team members.

- Backend: one new `DataSeeder` bean gated behind `--spring.profiles.active=demo`
- Frontend: "Try Demo" button on the login page

---

## Tier B — High Impact, Medium Effort
> Build after Tier A. Needs new data sync or moderate UI work.

### 7. Issues Sync & Analytics
**Impact:** ⭐⭐⭐ | **Effort:** ⭐⭐

Sync GitHub issues per repo and show: open vs closed count, average time to close,
issue age distribution (how long issues sit open), and most active issue reporters.
Real-world use: project managers track issue health alongside code activity.

- Backend: new `issues` table, sync from `/repos/{owner}/{repo}/issues` API, Flyway migration
- Frontend: new "Issues" tab on the Analytics page

---

### 8. Release Trend Chart
**Impact:** ⭐⭐⭐ | **Effort:** ⭐⭐

Releases per month bar chart showing shipping cadence over time.
Real-world use: teams track whether they are shipping faster or slower quarter-over-quarter.
The `releases` table already exists in the schema — just needs to be populated.

- Backend: populate the existing `releases` table via `/repos/{owner}/{repo}/releases` sync
- Frontend: new chart on the Repos page detail view

---

### 9. Stars & Forks Growth Over Time
**Impact:** ⭐⭐ | **Effort:** ⭐⭐

Daily snapshot of star and fork counts per repo, plotted as a growth curve.
Real-world use: open-source maintainers track repo growth after blog posts, HN submissions, etc.

- Backend: new `repo_stats_snapshots` table, daily cron job to record counts
- Frontend: new chart on the Repos page, toggleable Stars / Forks view

---

### 10. Compare Two Contributors Side-by-Side
**Impact:** ⭐⭐⭐ | **Effort:** ⭐⭐

Pick any two contributors and compare commits, PRs, reviews, lines added, avg merge time
side-by-side in the same delta-badge format as the existing Period Compare page.
Real-world use: tech leads benchmark team members during performance cycles.

- Backend: reuse existing `/analytics/overview` and `/analytics/commits/trend` endpoints
- Frontend: new "Contributors" tab on the existing `/compare` page, two login dropdowns

---

### 11. Compare Two Repos Side-by-Side
**Impact:** ⭐⭐⭐ | **Effort:** ⭐⭐

Pick any two tracked repos and compare commit velocity, PR merge rate, bus factor,
review coverage, and health score side-by-side.
Real-world use: CTOs decide where to invest engineering resources.

- Backend: reuse existing repo-scoped endpoints
- Frontend: new "Repositories" tab on the existing `/compare` page

---

### 12. Language Bytes Distribution Upgrade
**Impact:** ⭐⭐ | **Effort:** ⭐⭐

Currently the Languages tab counts repos by primary language (1 repo = 1 vote).
GitHub returns actual byte counts per language — upgrade to show real % of code by bytes.
Real-world use: accurate picture of the team's actual tech stack.

- Backend: sync `/repos/{owner}/{repo}/languages` on each repo sync, store in a `repo_languages` table
- Frontend: upgrade `LanguageDistribution` component to use byte-weighted percentages

---

### 13. Public Shareable Snapshots
**Impact:** ⭐⭐⭐ | **Effort:** ⭐⭐

Generate a short link (`/share/abc123`) for any repo or personal analytics view.
The link renders a read-only snapshot — no login required.
Real-world use: developers share their GitHub stats on LinkedIn, resumes, or team wikis.

- Backend: new `shared_snapshots` table, a `/share` endpoint that caches a snapshot in Redis (1hr TTL)
- Frontend: "Share" button on dashboard and repo pages, public `/share/[token]` route

---

### 14. Contributor Network Graph (Force-Directed)
**Impact:** ⭐⭐ | **Effort:** ⭐⭐

Upgrade the Collaboration page from bar charts to an interactive D3 force-directed graph.
Nodes = contributors, edges = review relationships weighted by review count.
Real-world use: visualise team silos — who only reviews their own PRs vs true collaborators.

- Backend: no changes — existing `/analytics/collaboration` endpoint is sufficient
- Frontend: integrate `react-force-graph-2d` or `d3-force`, replace bar charts

---

## Tier C — Medium Impact, Medium Effort
> Quality-of-life improvements that make daily use smoother.

### 15. In-App Notifications Bell
**Impact:** ⭐⭐ | **Effort:** ⭐⭐

A notification bell in the sidebar showing alerts like:
- "PR #42 has been open for 3 days — you haven't reviewed it yet"
- "Your streak breaks tomorrow if you don't commit"
- "Goal: 3 days left, you need 12 more commits to hit your monthly target"

Real-world use: passive reminders without leaving the app or checking email.

- Backend: new `/notifications` endpoint that derives alerts from existing data — no new sync
- Frontend: bell icon with badge count, dropdown panel in sidebar

---

### 16. Weekly Email Digest Improvements
**Impact:** ⭐⭐⭐ | **Effort:** ⭐⭐

The digest currently sends basic stats. Upgrade with:
- AI-generated coaching paragraph (already has Groq wired up)
- Visual ASCII sparkline for commit trend
- Top 3 PRs to review
- Goal progress summary

Real-world use: developers who don't check the dashboard daily still get meaningful weekly insights.

- Backend: extend `DigestService` to pull from existing analytics methods
- No frontend changes

---

### 17. Time-to-First-Response Metric
**Impact:** ⭐⭐⭐ | **Effort:** ⭐⭐

How long does it take from a PR being opened to the first review comment?
One of the most actionable metrics for improving team velocity.
Real-world use: engineering managers set SLAs like "first review within 4 hours."

- Backend: `first_review_at` column already exists on `pull_requests` — extend `/analytics/prs/lifecycle` response
- Frontend: new stat in the PR Lifecycle section

---

### 18. Sync Status Dashboard
**Impact:** ⭐⭐ | **Effort:** ⭐⭐

A dedicated page showing the sync health of every tracked repo:
last synced, next scheduled sync, number of commits/PRs/reviews fetched, any errors.
Real-world use: users know when their data is stale without guessing.

- Backend: expose `sync_jobs` table via a new `/repos/sync-status` endpoint
- Frontend: new table on the Repos page with status badges and a manual re-sync button

---

## Tier D — High Impact, High Effort
> Big bets. Worth building once the core is solid.

### 19. GitHub Actions CI/CD Analytics
**Impact:** ⭐⭐⭐ | **Effort:** ⭐⭐⭐

Sync GitHub Actions workflow runs per repo and show:
- Build success rate over time
- Average build duration trend
- Most frequent failure reasons
- Which commits most often break the build

Real-world use: DevOps teams track CI health as a first-class metric alongside code output.

- Backend: new `ci_runs` table, sync from `/repos/{owner}/{repo}/actions/runs`
- Frontend: new "CI / CD" tab on the Analytics page

---

### 20. Sprint / Milestone Tracking
**Impact:** ⭐⭐⭐ | **Effort:** ⭐⭐⭐

Map GitHub Milestones to sprints and show velocity per sprint:
commits, PRs merged, issues closed, and carry-over from last sprint.
Real-world use: engineering teams using GitHub Milestones as sprint boards get Jira-like analytics for free.

- Backend: sync `/repos/{owner}/{repo}/milestones` and associate issues/PRs to milestones
- Frontend: new "Sprints" page with a sprint selector and velocity cards

---

### 21. AI Coaching Chat
**Impact:** ⭐⭐⭐ | **Effort:** ⭐⭐⭐

A chat panel powered by Groq where developers ask questions like:
- "Why did my productivity drop last week?"
- "Which repo needs the most attention right now?"
- "Am I on track to hit my goals?"

The AI is given the user's analytics data as context and answers in plain English.
Real-world use: turns raw numbers into conversation — the most accessible form of insight.

- Backend: new `/analytics/chat` endpoint, pass analytics context to Groq with a system prompt
- Frontend: floating chat panel, conversation history stored in sessionStorage

---

### 22. Team Workload Balancing View
**Impact:** ⭐⭐⭐ | **Effort:** ⭐⭐⭐

Show each team member's current open PR count, review queue depth, and commit pace
in a single "workload" heatmap. Highlight who is overloaded vs underutilised.
Real-world use: tech leads redistribute review assignments to prevent burnout and bottlenecks.

- Backend: aggregate open PRs and pending reviews per contributor across a shared repo
- Frontend: new view on the Team page, colour-coded workload grid

---

### 23. Code Ownership Map
**Impact:** ⭐⭐⭐ | **Effort:** ⭐⭐⭐

Show which files or directories each contributor owns based on commit history.
Highlight files with no clear owner (bus factor = 1 or unknown).
Real-world use: new team members know who to ask for code reviews; managers spot knowledge silos.

- Backend: analyse file paths from commit data (needs GitHub Tree API), compute ownership %
- Frontend: tree view with contributor avatars overlaid per directory

---

## Summary Table

| # | Feature | Impact | Effort | Tier |
|---|---------|--------|--------|------|
| 1 | Code Churn Leaderboard | ⭐⭐⭐ | ⭐ | A |
| 2 | PR Merge Rate Over Time | ⭐⭐⭐ | ⭐ | A |
| 3 | Reviewer Coverage | ⭐⭐⭐ | ⭐ | A |
| 4 | Keyboard Shortcuts | ⭐⭐ | ⭐ | A |
| 5 | PDF / Print Export | ⭐⭐ | ⭐ | A |
| 6 | Demo Seed Script | ⭐⭐⭐ | ⭐ | A |
| 7 | Issues Sync & Analytics | ⭐⭐⭐ | ⭐⭐ | B |
| 8 | Release Trend Chart | ⭐⭐⭐ | ⭐⭐ | B |
| 9 | Stars & Forks Growth | ⭐⭐ | ⭐⭐ | B |
| 10 | Compare Two Contributors | ⭐⭐⭐ | ⭐⭐ | B |
| 11 | Compare Two Repos | ⭐⭐⭐ | ⭐⭐ | B |
| 12 | Language Bytes Upgrade | ⭐⭐ | ⭐⭐ | B |
| 13 | Public Shareable Snapshots | ⭐⭐⭐ | ⭐⭐ | B |
| 14 | Contributor Network Graph | ⭐⭐ | ⭐⭐ | B |
| 15 | In-App Notifications Bell | ⭐⭐ | ⭐⭐ | C |
| 16 | Weekly Digest Improvements | ⭐⭐⭐ | ⭐⭐ | C |
| 17 | Time-to-First-Response | ⭐⭐⭐ | ⭐⭐ | C |
| 18 | Sync Status Dashboard | ⭐⭐ | ⭐⭐ | C |
| 19 | GitHub Actions Analytics | ⭐⭐⭐ | ⭐⭐⭐ | D |
| 20 | Sprint / Milestone Tracking | ⭐⭐⭐ | ⭐⭐⭐ | D |
| 21 | AI Coaching Chat | ⭐⭐⭐ | ⭐⭐⭐ | D |
| 22 | Team Workload Balancing | ⭐⭐⭐ | ⭐⭐⭐ | D |
| 23 | Code Ownership Map | ⭐⭐⭐ | ⭐⭐⭐ | D |

---

## Suggested Build Order

1. **Start with Tier A** — all six features use existing data, ship in days, and make the app feel complete.
2. **Pick 2–3 from Tier B** — Issues Analytics, Compare Contributors, and Shareable Snapshots have the best effort-to-impact ratio.
3. **Tier C** — polish the experience for daily active users (notifications, digest, sync status).
4. **Tier D** — tackle one big-bet feature once the core is stable. AI Chat or GitHub Actions are the most differentiating.
