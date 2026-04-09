#!/usr/bin/env bash
set -euo pipefail

ROOT_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$ROOT_PATH/infrastructure/docker/docker-compose.yml"
SKIP_TESTS="${SKIP_TESTS:-true}"
INCLUDE_MONITORING="${INCLUDE_MONITORING:-false}"

build_module() {
  local module_path="$1"
  echo "[rebuild-local] Building module: $module_path"
  if [[ "$SKIP_TESTS" == "true" ]]; then
    (cd "$module_path" && mvn clean package -Ddependency.check.skip=true -DskipTests)
  else
    (cd "$module_path" && mvn clean package -Ddependency.check.skip=true)
  fi
}

echo "[rebuild-local] Root path: $ROOT_PATH"

if [[ -d "$ROOT_PATH/shared" ]]; then
  while IFS= read -r -d '' pom; do
    build_module "$(dirname "$pom")"
  done < <(find "$ROOT_PATH/shared" -name pom.xml -type f -print0)
fi

build_module "$ROOT_PATH/services/elvo-identity-service"
build_module "$ROOT_PATH/services/elvo-wallet-service"
build_module "$ROOT_PATH/services/elvo-billing-service"

echo "[rebuild-local] Recreating runtime stack with rebuilt images..."
if [[ "$INCLUDE_MONITORING" == "true" ]]; then
  docker compose -f "$COMPOSE_FILE" --profile monitoring up -d --build --force-recreate
else
  docker compose -f "$COMPOSE_FILE" up -d --build --force-recreate
fi

echo "[rebuild-local] Current status:"
docker compose -f "$COMPOSE_FILE" ps
if [[ "$INCLUDE_MONITORING" == "true" ]]; then
  echo "[rebuild-local] Monitoring URLs: Prometheus http://localhost:9090, Grafana http://localhost:3000, Alertmanager http://localhost:9093"
fi
