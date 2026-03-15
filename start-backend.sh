#!/bin/bash
# Start the GitHub Analytics backend with all env vars loaded from backend/.env
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/backend/.env"
JAR="$SCRIPT_DIR/backend/target/github-analytics-*.jar"
JAVA_HOME_PATH="/home/hari-om-sharma/.sdkman/candidates/java/current"
LOG_FILE="/tmp/backend.log"

# Kill any existing instance
fuser -k 8080/tcp 2>/dev/null || true
sleep 1

# Load env vars
if [ -f "$ENV_FILE" ]; then
  set -a
  source "$ENV_FILE"
  set +a
  echo "Loaded env from $ENV_FILE"
else
  echo "WARNING: $ENV_FILE not found"
fi

echo "Starting backend... (logs → $LOG_FILE)"
nohup "$JAVA_HOME_PATH/bin/java" \
  -DGROQ_API_KEY="${GROQ_API_KEY:-}" \
  -jar $JAR \
  --spring.profiles.active=dev \
  > "$LOG_FILE" 2>&1 &

echo "Backend PID: $!"
echo "Waiting for startup..."
sleep 20
curl -sf http://localhost:8080/api/v1/actuator/health && echo "Backend is UP" || echo "Backend not yet ready — check $LOG_FILE"
