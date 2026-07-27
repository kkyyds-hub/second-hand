#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${SECONDHAND_ENV_FILE:-$root/.env.production}"
compose=(docker compose -f "$root/compose.production.yml" --env-file "$env_file")

diagnose_failure() {
  status=$?
  echo "Deployment failed with status $status. Containers are preserved for diagnosis." >&2
  "${compose[@]}" ps -a || true
  "${compose[@]}" logs --tail=200 migration || true
  "${compose[@]}" logs --tail=200 backend || true
  "${compose[@]}" logs --tail=100 user-web || true
  "${compose[@]}" logs --tail=100 admin-web || true
  exit "$status"
}
trap diagnose_failure ERR

command -v docker >/dev/null || { echo 'Docker is required.' >&2; exit 1; }
docker compose version >/dev/null || { echo 'Docker Compose v2 is required.' >&2; exit 1; }
[[ -f "$env_file" ]] || { echo "Missing production env file: $env_file" >&2; exit 1; }

required=(SECONDHAND_MYSQL_ROOT_PASSWORD SECONDHAND_MYSQL_DATABASE SECONDHAND_MYSQL_USER SECONDHAND_MYSQL_PASSWORD SECONDHAND_REDIS_PASSWORD SECONDHAND_MONGO_ROOT_USER SECONDHAND_MONGO_ROOT_PASSWORD SECONDHAND_MONGO_DATABASE SECONDHAND_RABBITMQ_USER SECONDHAND_RABBITMQ_PASSWORD SECONDHAND_RABBITMQ_VHOST SECONDHAND_JWT_ADMIN_SECRET SECONDHAND_JWT_USER_SECRET SECONDHAND_UPLOAD_SIGN_SECRET SECONDHAND_PAYMENT_MOCK_SIGN SECONDHAND_PUBLIC_URL SECONDHAND_MAIL_HOST SECONDHAND_MAIL_USERNAME SECONDHAND_MAIL_PASSWORD)
for key in "${required[@]}"; do
  value="$(awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$env_file")"
  if [[ -z "$value" || "$value" == *replace-with-* || "$value" == *change-me* ]]; then
    echo "Invalid or placeholder value for $key in $env_file" >&2
    exit 1
  fi
done

deployment_mode="$(awk -F= '$1 == "SECONDHAND_DEPLOYMENT_MODE" {sub(/^[^=]*=/, ""); print; exit}' "$env_file")"
[[ "$deployment_mode" == "acceptance" || "$deployment_mode" == "production" ]] \
  || { echo 'SECONDHAND_DEPLOYMENT_MODE must be acceptance or production.' >&2; exit 1; }
mail_host="$(awk -F= '$1 == "SECONDHAND_MAIL_HOST" {sub(/^[^=]*=/, ""); print; exit}' "$env_file")"
[[ "$mail_host" != *example.invalid* && "$mail_host" != "mail.example.invalid" ]] \
  || { echo 'SECONDHAND_MAIL_HOST must not use an example.invalid hostname.' >&2; exit 1; }
public_url="$(awk -F= '$1 == "SECONDHAND_PUBLIC_URL" {sub(/^[^=]*=/, ""); print; exit}' "$env_file")"
if [[ "$deployment_mode" == "production" && "$public_url" =~ ^https?://(localhost|127\.0\.0\.1)(:|/|$) ]]; then
  echo 'SECONDHAND_PUBLIC_URL must not point to localhost in production mode.' >&2
  exit 1
fi
if [[ "$deployment_mode" == "acceptance" && "$public_url" =~ ^https?://(localhost|127\.0\.0\.1)(:|/|$) ]]; then
  echo 'Warning: localhost public URL is allowed only for acceptance mode.' >&2
fi

"${compose[@]}" config >/dev/null
"${compose[@]}" up -d --build
"$root/scripts/prod/smoke-test.sh" "$env_file"
echo 'Production deployment completed. Logs: docker compose -f compose.production.yml --env-file .env.production logs --tail=200'
