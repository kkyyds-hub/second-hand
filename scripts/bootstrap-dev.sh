#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
database_dir="$repo_root/database"

command -v docker >/dev/null || { printf 'docker is required.\n' >&2; exit 1; }
command -v mysql >/dev/null || { printf 'mysql client is required.\n' >&2; exit 1; }

mysql_protocol="${MYSQL_ADMIN_PROTOCOL:-socket}"
mysql_args=("--protocol=$mysql_protocol" "-u${MYSQL_ADMIN_USER:-root}")

if [[ "$mysql_protocol" == "socket" && -n "${MYSQL_ADMIN_SOCKET:-}" ]]; then
  mysql_args+=("--socket=${MYSQL_ADMIN_SOCKET}")
elif [[ "$mysql_protocol" == "tcp" ]]; then
  mysql_args+=("-h${MYSQL_ADMIN_HOST:-127.0.0.1}" "-P${MYSQL_ADMIN_PORT:-3306}")
fi

run_mysql() {
  if [[ -n "${MYSQL_ADMIN_PASSWORD:-}" ]]; then
    MYSQL_PWD="$MYSQL_ADMIN_PASSWORD" mysql "${mysql_args[@]}" "$@"
  else
    mysql "${mysql_args[@]}" "$@"
  fi
}

wait_for_service() {
  local service="$1"
  local container_id
  container_id="$(docker compose -f "$repo_root/compose.yaml" ps -q "$service")"
  [[ -n "$container_id" ]] || { printf 'missing container for %s\n' "$service" >&2; exit 1; }

  local attempt status
  for attempt in $(seq 1 30); do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")"
    if [[ "$status" == "healthy" ]]; then
      return 0
    fi
    sleep 2
  done

  printf '%s did not become healthy (last status: %s)\n' "$service" "$status" >&2
  exit 1
}

cd "$repo_root"
docker compose up -d
wait_for_service redis
wait_for_service mongodb
wait_for_service rabbitmq

run_mysql < "$database_dir/00_rebuild_secondhand2.sql"
run_mysql < "$database_dir/02_create_dev_app_user.sql"
docker compose exec -T mongodb mongosh --file /dev/stdin < "$database_dir/01_seed_mongodb.js"

printf 'Development infrastructure bootstrap complete.\n'
