#!/usr/bin/env bash
set -euo pipefail

ROOT_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$ROOT_PATH/infrastructure/docker/docker-compose.yml"

echo "[reset-local] Root path: $ROOT_PATH"
echo "[reset-local] Removing containers, networks, and volumes for this stack..."
docker compose -f "$COMPOSE_FILE" down --volumes --remove-orphans

echo "[reset-local] Reset completed."
