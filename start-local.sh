#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# start-local.sh — Start the full GitHub Analytics stack locally
#
# Just run:  ./start-local.sh
#
# No need to source nvm/sdkman first — this script handles that.
# ---------------------------------------------------------------------------

set -e

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

log()  { echo -e "${GREEN}[start-local]${NC} $*"; }
warn() { echo -e "${YELLOW}[start-local]${NC} $*"; }
die()  { echo -e "${RED}[start-local] ERROR:${NC} $*" >&2; exit 1; }

# ---------- Load SDKMAN (Java + Maven) ----------
SDKMAN_INIT="$HOME/.sdkman/bin/sdkman-init.sh"
if [ -s "$SDKMAN_INIT" ]; then
  export SDKMAN_DIR="$HOME/.sdkman"
  # shellcheck disable=SC1090
  source "$SDKMAN_INIT"
else
  # Fallback: common manual install locations
  for candidate in /usr/lib/jvm/java-21*/bin /usr/local/lib/jvm/java-21*/bin; do
    [ -d "$candidate" ] && export PATH="$candidate:$PATH" && break
  done
fi

# ---------- Load NVM (Node + npm) ----------
NVM_INIT="$HOME/.nvm/nvm.sh"
if [ -s "$NVM_INIT" ]; then
  export NVM_DIR="$HOME/.nvm"
  # shellcheck disable=SC1090
  source "$NVM_INIT"
else
  # Fallback: try known nvm node version path
  for candidate in "$HOME/.nvm/versions/node"/*/bin; do
    [ -d "$candidate" ] && export PATH="$candidate:$PATH" && break
  done
fi

# ---------- Check prerequisites ----------
command -v docker &>/dev/null || die "Docker not found. Install from https://docs.docker.com/get-docker/"
command -v java   &>/dev/null || die "Java not found. Install via: sdk install java 21.0.3-tem"
command -v mvn    &>/dev/null || die "Maven not found. Install via: sdk install maven"
command -v node   &>/dev/null || die "Node.js not found. Install via: nvm install 20"
command -v npm    &>/dev/null || die "npm not found."

log "Using: java $(java -version 2>&1 | head -1 | awk -F '"' '{print $2}')  |  mvn $(mvn -v 2>/dev/null | head -1 | awk '{print $3}')  |  node $(node -v)  |  npm $(npm -v)"

# ---------- Load backend .env ----------
if [ -f "$BACKEND/.env" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$BACKEND/.env"
  set +a
  log "Loaded backend/.env"
else
  warn "No backend/.env found — GitHub OAuth will not work"
fi

# ---------- Detect Docker permissions ----------
# If docker isn't accessible, re-exec this script under the docker group
if ! docker info &>/dev/null 2>&1; then
  if id -nG "$USER" | grep -qw docker; then
    exec sg docker "$0" "$@"
  else
    die "Cannot connect to Docker. Run: sudo usermod -aG docker \$USER  then log out and back in"
  fi
fi

# ---------- Start infrastructure ----------
log "Starting PostgreSQL, Redis, and Kafka via docker-compose..."
docker compose -f "$ROOT/docker-compose.yml" up -d 2>/dev/null || \
  docker-compose -f "$ROOT/docker-compose.yml" up -d

log "Waiting for PostgreSQL..."
until docker exec ga-postgres pg_isready -U postgres &>/dev/null; do sleep 1; done
log "PostgreSQL ready."

log "Waiting for Redis..."
until docker exec ga-redis redis-cli ping &>/dev/null; do sleep 1; done
log "Redis ready."

log "Waiting for Kafka (may take ~30s on first start)..."
for i in $(seq 1 30); do
  if docker exec ga-kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 &>/dev/null; then
    log "Kafka ready."
    break
  fi
  sleep 3
  [ "$i" -eq 30 ] && warn "Kafka health check timed out — continuing anyway (topics auto-create on first use)"
done

# ---------- Build backend if no JAR exists ----------
JAR=$(ls "$BACKEND"/target/github-analytics-*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  log "No JAR found — building backend (first run only, ~60s)..."
  (cd "$BACKEND" && mvn package -DskipTests --no-transfer-progress -q)
  JAR=$(ls "$BACKEND"/target/github-analytics-*.jar | head -1)
  log "Build complete: $JAR"
else
  log "Using existing JAR: $(basename "$JAR")  (run 'mvn package' in backend/ to rebuild)"
fi

# ---------- Start backend ----------
log "Starting Spring Boot backend on port 8080..."
fuser -k 8080/tcp 2>/dev/null || true
nohup java \
  -Xms64m -Xmx256m \
  -jar "$JAR" \
  --spring.profiles.active=dev \
  > /tmp/ga-backend.log 2>&1 &
echo $! > /tmp/ga-backend.pid
log "Backend PID: $(cat /tmp/ga-backend.pid) — logs: /tmp/ga-backend.log"

log "Waiting for backend to start..."
for i in $(seq 1 60); do
  if curl -sf http://localhost:8080/api/v1/actuator/health &>/dev/null; then
    log "Backend ready at http://localhost:8080"
    break
  fi
  sleep 2
  [ "$i" -eq 60 ] && die "Backend did not start after 120s. Check: tail -f /tmp/ga-backend.log"
done

# ---------- Start frontend ----------
log "Starting Next.js frontend on port 3000..."
(
  cd "$FRONTEND"
  nohup npm run dev > /tmp/ga-frontend.log 2>&1 &
  echo $! > /tmp/ga-frontend.pid
)
log "Frontend PID: $(cat /tmp/ga-frontend.pid) — logs: /tmp/ga-frontend.log"

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
log "  Logs:"
log "    tail -f /tmp/ga-backend.log"
log "    tail -f /tmp/ga-frontend.log"
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
