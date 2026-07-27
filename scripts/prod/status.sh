#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${SECONDHAND_ENV_FILE:-$root/.env.production}"
docker compose -f "$root/compose.production.yml" --env-file "$env_file" ps
