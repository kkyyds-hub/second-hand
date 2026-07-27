#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${1:-${SECONDHAND_ENV_FILE:-$root/.env.production}}"
compose=(docker compose -f "$root/compose.production.yml" --env-file "$env_file")

for attempt in $(seq 1 30); do
  if curl --fail --silent --show-error http://127.0.0.1/healthz | grep -q '"status":"UP"'; then
    break
  fi
  sleep 2
done
curl --fail --silent --show-error http://127.0.0.1/healthz | grep -q '"status":"UP"'
curl --fail --silent --show-error http://127.0.0.1/ >/dev/null
curl --fail --silent --show-error http://127.0.0.1:8081/ >/dev/null
"${compose[@]}" ps --status running | grep -Eq 'mysql|redis|mongodb|rabbitmq|backend|user-web|admin-web'
echo 'Smoke test passed.'
