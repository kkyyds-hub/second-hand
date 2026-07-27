#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${1:-${SECONDHAND_ENV_FILE:-$root/.env.production}}"
compose=(docker compose)
if [[ -n "${SECONDHAND_COMPOSE_PROJECT:-}" ]]; then
  compose+=(-p "$SECONDHAND_COMPOSE_PROJECT")
fi
compose+=(-f "$root/compose.production.yml" --env-file "$env_file")

fail() {
  echo "Smoke test failed: $*" >&2
  exit 1
}

expect_up() {
  local url="$1"
  curl --fail --silent --show-error "$url" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' \
    || fail "health endpoint did not return UP: $url"
}

services=(mysql redis mongodb rabbitmq backend user-web admin-web)
for service in "${services[@]}"; do
  container_id="$("${compose[@]}" ps -q "$service")"
  [[ -n "$container_id" ]] || fail "service container missing: $service"
  state="$(docker inspect --format '{{.State.Status}}' "$container_id")"
  [[ "$state" == "running" ]] || fail "service is not running: $service ($state)"
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id")"
  [[ "$health" == "healthy" ]] || fail "service is not healthy: $service ($health)"
  restarts="$(docker inspect --format '{{.RestartCount}}' "$container_id")"
  [[ "$restarts" == "0" ]] || fail "service restarted unexpectedly: $service ($restarts)"
done

migration_id="$("${compose[@]}" ps -aq migration)"
[[ -n "$migration_id" ]] || fail 'migration container missing'
[[ "$(docker inspect --format '{{.State.Status}}' "$migration_id")" == "exited" ]] || fail 'migration did not exit'
[[ "$(docker inspect --format '{{.State.ExitCode}}' "$migration_id")" == "0" ]] || fail 'migration exit code is non-zero'
[[ -z "$("${compose[@]}" ps -aq demo-seed)" ]] || fail 'demo-seed ran without the demo profile'

for attempt in $(seq 1 30); do
  if curl --fail --silent --show-error http://127.0.0.1/api/readyz \
      | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    break
  fi
  sleep 2
done
expect_up http://127.0.0.1/api/healthz
expect_up http://127.0.0.1/api/readyz
expect_up http://127.0.0.1:8081/api/healthz
expect_up http://127.0.0.1:8081/api/readyz
curl --fail --silent --show-error http://127.0.0.1/ >/dev/null
curl --fail --silent --show-error http://127.0.0.1:8081/ >/dev/null
smtp_probe=$'getent hosts "$SECONDHAND_MAIL_HOST"\ntimeout 15 bash -ec \'exec 3<>/dev/tcp/"$SECONDHAND_MAIL_HOST"/"$SECONDHAND_MAIL_PORT"; exec 3>&-\''
"${compose[@]}" exec -T backend sh -ec "$smtp_probe"
echo 'Smoke test passed.'
