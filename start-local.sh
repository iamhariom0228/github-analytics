#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# start-local.sh — Start the full GitHub Analytics stack locally
#
# Prerequisites:
#   - Docker + Docker Compose installed
#   - Java 21 (e.g. via SDKMAN: sdk install java 21.0.3-tem)
#   - Node.js 20 (e.g. via nvm: nvm install 20)
#   - GitHub OAuth App with callback: http://localhost:8080/api/v1/auth/github/callback
#     (Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET below or in backend/.env)
#
# Quick start:
#   chmod +x start-local.sh && ./start-local.sh
# ---------------------------------------------------------------------------

set -e

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

log()  { echo -e "${GREEN}[start-local]${NC} $*"; }
warn() { echo -e "${YELLOW}[start-local]${NC} $*"; }
die()  { echo -e "${RED}[start-local] ERROR:${NC} $*" >&2; exit 1; }

# ---------- Check prerequisites ----------
command -v docker &>/dev/null    || die "Docker not found. Install from https://docs.docker.com/get-docker/"
command -v java   &>/dev/null    || die "Java not found. Install via: sdk install java 21.0.3-tem"
command -v mvn    &>/dev/null    || die "Maven not found. Install via: sdk install maven 3.9.6"
command -v node   &>/dev/null    || die "Node.js not found. Install via: nvm install 20"
command -v npm    &>/dev/null    || die "npm not found."

# ---------- Load backend .env ----------
if [ -f "$BACKEND/.env" ]; then
  export $(grep -v '^#' "$BACKEND/.env" | xargs)
  log "Loaded $BACKEND/.env"
else
  warn "No backend/.env found — using defaults (GitHub OAuth will be placeholder)"
fi

# ---------- Start infrastructure ----------
log "Starting PostgreSQL, Redis, and Kafka via docker-compose..."
docker compose -f "$ROOT/docker-compose.yml" up -d --wait 2>/dev/null || \
  docker-compose -f "$ROOT/docker-compose.yml" up -d

log "Waiting for PostgreSQL to be ready..."
until docker exec ga-postgres pg_isready -U postgres &>/dev/null; do
  sleep 1
done
log "PostgreSQL ready."

log "Waiting for Redis to be ready..."
until docker exec ga-redis redis-cli ping &>/dev/null; do
  sleep 1
done
log "Redis ready."

# ---------- Start backend ----------
log "Starting Spring Boot backend on port 8080 (profile: dev)..."
(
  cd "$BACKEND"
  export SPRING_PROFILES_ACTIVE=dev
  mvn spring-boot:run --no-transfer-progress \
    -Dspring-boot.run.jvmArguments="-Xms64m -Xmx256m" \
    2>&1 | sed "s/^/[backend] /" &
  echo $! > /tmp/ga-backend.pid
)

log "Waiting for backend to start..."
for i in $(seq 1 60); do
  if curl -sf http://localhost:8080/api/v1/actuator/health &>/dev/null; then
    log "Backend ready at http://localhost:8080"
    break
  fi
  sleep 2
  if [ "$i" -eq 60 ]; then
    die "Backend did not start after 120s. Check logs above."
  fi
done

# ---------- Start frontend ----------
log "Starting Next.js frontend on port 3000..."
(
  cd "$FRONTEND"
  BACKEND_URL=http://localhost:8080 npm run dev 2>&1 | sed "s/^/[frontend] /" &
  echo $! > /tmp/ga-frontend.pid
)

sleep 3
log ""
log "============================================================"
log "  GitHub Analytics Dashboard is running!"
log "============================================================"
log ""
log "  Frontend:     http://localhost:3000"
log "  Backend API:  http://localhost:8080/api/v1"
log "  Health:       http://localhost:8080/api/v1/actuator/health"
log ""
log "  Demo login (no GitHub OAuth needed):"
log "  → http://localhost:8080/api/v1/dev/demo-login"
log ""
log "  Login with GitHub (requires OAuth App):"
log "  → http://localhost:3000  then click 'Connect GitHub'"
log ""
log "  Press Ctrl+C to stop everything."
log "============================================================"

# ---------- Wait and cleanup ----------
cleanup() {
  log "Shutting down..."
  [ -f /tmp/ga-backend.pid ]  && kill "$(cat /tmp/ga-backend.pid)"  2>/dev/null || true
  [ -f /tmp/ga-frontend.pid ] && kill "$(cat /tmp/ga-frontend.pid)" 2>/dev/null || true
  docker compose -f "$ROOT/docker-compose.yml" stop 2>/dev/null || \
    docker-compose -f "$ROOT/docker-compose.yml" stop 2>/dev/null || true
  log "Done."
}
trap cleanup EXIT INT TERM

wait
