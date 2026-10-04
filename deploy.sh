#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/home/ubuntu/Aggregator-Backend"
BACKUP_DIR="${APP_DIR}/backups"
TIMESTAMP="$(date +'%Y%m%d_%H%M%S')"

echo "=== [Aggregator-Backend] Deployment Started: ${TIMESTAMP} ==="

cd "${APP_DIR}"

# 1. Zero Data Loss Safety: Backup PostgreSQL Database
echo "[Step 1/5] Backing up PostgreSQL database..."
mkdir -p "${BACKUP_DIR}"
PGPASSWORD="aggregator_secret" pg_dump -U aggregator -h localhost aggregator_registry > "${BACKUP_DIR}/db_backup_${TIMESTAMP}.sql"
echo "Database snapshot saved to: ${BACKUP_DIR}/db_backup_${TIMESTAMP}.sql"

# Keep only the last 10 backups to prevent disk overflow
ls -t "${BACKUP_DIR}"/db_backup_*.sql 2>/dev/null | tail -n +11 | xargs -r rm -f

# 2. Zero Data Loss Safety: Backup Local Plugin Working Trees and Configs
echo "[Step 2/5] Preserving data repositories and local configs..."
mkdir -p "${BACKUP_DIR}/data_snapshots"
if [ -d "${APP_DIR}/data/plugins-repo" ]; then
    tar -czf "${BACKUP_DIR}/data_snapshots/plugins_repo_${TIMESTAMP}.tar.gz" -C "${APP_DIR}/data" plugins-repo
    ls -t "${BACKUP_DIR}/data_snapshots"/plugins_repo_*.tar.gz 2>/dev/null | tail -n +11 | xargs -r rm -f
fi

# 3. Pull latest changes if git remote is configured and clean
echo "[Step 3/5] Checking Git updates..."
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    # Stash any local working state if present
    git stash -u >/dev/null 2>&1 || true
    git pull origin main --rebase || true
fi

# 4. Verify Built Artifact
echo "[Step 4/5] Checking target application artifact..."
if [ ! -f "${APP_DIR}/target/Aggregator-backend-0.0.1-SNAPSHOT.jar" ]; then
    echo "ERROR: Target JAR not found at ${APP_DIR}/target/Aggregator-backend-0.0.1-SNAPSHOT.jar"
    exit 1
fi

# 5. Service Reload with Health Verification
echo "[Step 5/5] Restarting aggregator-backend systemd service..."
sudo systemctl restart aggregator-backend

# Health probe loop (wait up to 90s for service to come alive on port 8080)
echo "Waiting for health check on port 8080..."
MAX_ATTEMPTS=30
ATTEMPT=0
SUCCESS=false

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    sleep 3
    ATTEMPT=$((ATTEMPT + 1))
    HTTP_STATUS="$(curl -s -o /dev/null -w "%{http_code}" -H "X-Origin-Secret: dev-origin-secret" http://localhost:8080/api/v1/plugins?channel=STABLE || true)"
    if [ "$HTTP_STATUS" = "200" ]; then
        SUCCESS=true
        break
    fi
    echo "Probing origin... attempt ${ATTEMPT}/${MAX_ATTEMPTS} (HTTP status: ${HTTP_STATUS})"
done

if [ "$SUCCESS" = true ]; then
    echo "=== [Aggregator-Backend] Deployment SUCCESS: Service is healthy and serving traffic ==="
    exit 0
else
    echo "ERROR: Health check failed after 90 seconds. Reviewing recent systemd journal:"
    sudo journalctl -u aggregator-backend -n 25 --no-pager
    exit 1
fi
