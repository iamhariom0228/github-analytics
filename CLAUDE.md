# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Backend (Spring Boot / Maven)
```bash
# Build
cd backend && mvn package -DskipTests          # fast build
cd backend && mvn clean package                # clean rebuild with tests

# Run (always load .env first)
cd backend && set -a && source .env && set +a && java -jar target/github-analytics-*.jar

# Or use the convenience script
./start-backend.sh

# Test
cd backend && mvn test                                          # all tests
cd backend && mvn test -Dtest=AnalyticsServiceTest              # single class
cd backend && mvn test -Dtest=AnalyticsServiceTest#methodName   # single method
```

### Frontend (Next.js)
```bash
cd frontend && npm run dev        # development server on port 3000
cd frontend && npm run build      # production build
cd frontend && npm run lint       # ESLint
cd frontend && npm run type-check # TypeScript check without emitting
```

### Full stack
```bash
./start-local.sh   # starts Docker (Postgres, Redis, Kafka), backend, and frontend
```

## Architecture Overview

This is a GitHub analytics platform. The **Spring Boot backend** exposes a REST API at `localhost:8080/api/v1`. The **Next.js frontend** at `localhost:3000` proxies requests to it via rewrites (`/api/backend/*` → `http://localhost:8080/api/v1/*`).

### Auth flow
1. User authenticates via GitHub OAuth → backend generates a JWT.
2. JWT stored as an HTTP-only cookie, set by the Next.js API route `/api/auth/callback` (not by the backend directly — the backend's `Set-Cookie` would be stripped by the proxy).
3. `frontend/src/middleware.ts` extracts the JWT cookie and forwards it as `Authorization: Bearer {token}` on every proxied request to the backend.
4. **Demo login** hits `/api/demo-login` (Next.js route) which calls `/api/v1/dev/demo-token` on the backend with `cache: "no-store"` to always get a fresh token.
5. **Logout** hits `/api/auth/logout` (Next.js route) which calls the backend server-side to revoke the token in Redis, then clears the cookie itself.

### Backend package structure
```
com.gitanalytics
├── auth/        GitHub OAuth, JWT generation/validation, user entity
├── ingestion/   Repo sync: commits, PRs, releases, issues, language bytes
│                GitHubApiClient (GitHub REST/GraphQL), Kafka producers,
│                scheduled cron jobs (RepoStatsSnapshotScheduler)
├── analytics/   All analytics endpoints and computation: heatmaps,
│                PR lifecycle, streaks, leaderboards, AI summaries,
│                comparison, shareable snapshots
├── digest/      Weekly email digest composition and Kafka consumer
└── shared/      JwtAuthenticationFilter, RateLimitFilter, SecurityConfig,
                 GroqApiClient (AI), global exception handling, ApiResponse<T>
```

### Key backend patterns
- **DAO layer**: services depend on DAO interfaces (e.g. `AnalyticsDao`, `CommitDao`), not repositories directly. Implementations in `dao/impl/`.
- **Response wrapper**: all endpoints return `ResponseEntity<ApiResponse<T>>` using the `ApiResponse.ok(data)` helper.
- **User identity**: controllers extract user from `@AuthenticationPrincipal UserDetails principal`; UUID parsed with `UUID.fromString(principal.getUsername())`.
- **Redis caching**: expensive computations (dashboard, streaks, AI summaries) are manually cached in Redis with TTLs defined in `AppProperties`. Cache keys prefixed with `ga:`.
- **JWT revocation**: `revokeToken()` stores a SHA-256 hash of the token in Redis at `ga:token:revoked:{hash}`. `isTokenValid()` checks `Boolean.FALSE.equals(redisTemplate.hasKey(...))` — note this is fail-closed: a Redis outage treats all tokens as revoked.
- **Flyway migrations**: SQL files in `backend/src/main/resources/db/migration/` (V1–V9).
- **Encryption**: GitHub OAuth access tokens are stored AES-GCM encrypted in the DB (key from `ENCRYPTION_KEY` env var).

### Frontend patterns
- **API calls**: `src/lib/api/client.ts` — axios instance with `baseURL: "/api/backend"`. All typed API functions call `unwrap()` to extract `data` from `ApiResponse<T>`.
- **Data fetching**: `src/hooks/useAnalytics.ts` — all React Query hooks live here. Default stale time is 5 minutes; AI summaries use 6 hours.
- **Route groups**: `(app)/` requires auth (enforced by middleware), `(public)/` is open.
- **Types**: `src/types/index.ts` mirrors backend DTOs.

## Infrastructure

Services started by `./start-local.sh` or `docker-compose up`:
- **PostgreSQL 16**: `localhost:5432`, database `gitanalytics`
- **Redis 7**: `localhost:6379`
- **Kafka 3.7**: `localhost:9092`, consumer group `github-analytics`

## Environment variables

`backend/.env` (required, not committed):
```
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
JWT_SECRET=               # min 32 chars
ENCRYPTION_KEY=           # exactly 32 chars for AES-256
GROQ_API_KEY=             # for AI summaries
COOKIE_SECURE=false       # set false for HTTP localhost dev
```

`frontend/.env.local`:
```
BACKEND_URL=http://localhost:8080
NEXT_PUBLIC_APP_URL=http://localhost:3000
```
