#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /absolute/backup-directory" >&2
  exit 1
fi
target="$1"
[[ "$target" = /* ]] || { echo 'Backup directory must be an absolute path outside the repository.' >&2; exit 1; }
mkdir -p "$target"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${SECONDHAND_ENV_FILE:-$root/.env.production}"
compose=(docker compose -f "$root/compose.production.yml" --env-file "$env_file")
stamp="$(date +%Y%m%d-%H%M%S)"

set -a; source "$env_file"; set +a
"${compose[@]}" exec -T mysql sh -ec 'exec mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --single-transaction --routines --events "$MYSQL_DATABASE"' > "$target/mysql-$stamp.sql"
"${compose[@]}" exec -T mongodb sh -ec 'mongodump --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --db "$MONGO_INITDB_DATABASE" --archive' > "$target/mongo-$stamp.archive"
"${compose[@]}" cp backend:/data/uploads "$target/uploads-$stamp"
chmod 600 "$target/mysql-$stamp.sql" "$target/mongo-$stamp.archive"
echo "Backup written to $target. RabbitMQ messages are transient runtime data; export definitions separately when topology changes."
