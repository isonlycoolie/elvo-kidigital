#!/usr/bin/env bash
set -euo pipefail

ROOT_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$ROOT_PATH/infrastructure/docker/docker-compose.yml"

echo "[stop-local] Root path: $ROOT_PATH"
echo "[stop-local] Stopping runtime services (volumes preserved)..."
docker compose -f "$COMPOSE_FILE" stop

echo "[stop-local] Current status:"
docker compose -f "$COMPOSE_FILE" ps
