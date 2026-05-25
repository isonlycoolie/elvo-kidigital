#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MAVEN_ARGS=(clean verify -Ddependency.check.skip=true -B -ntp)

SERVICES=(
  "services/elvo-identity-service"
  "services/elvo-wallet-service"
  "services/elvo-billing-service"
  "services/elvo-account-management-service"
)

echo "[ci-test] Repository root: ${ROOT_DIR}"

for service_path in "${SERVICES[@]}"; do
  project_dir="${ROOT_DIR}/${service_path}"
  if [[ ! -f "${project_dir}/pom.xml" ]]; then
    echo "[ci-test] ERROR: missing pom.xml at ${project_dir}" >&2
    exit 1
  fi

  echo "[ci-test] Running tests in ${service_path}"
  (
    cd "${project_dir}"
    mvn "${MAVEN_ARGS[@]}"
  )
done

echo "[ci-test] All service test suites passed."
