#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
sql_file="$script_dir/00_rebuild_secondhand2.sql"
mongo_file="$script_dir/01_seed_mongodb.js"
account_file="$script_dir/02_create_dev_app_user.sql"
readme_file="$script_dir/README.md"
compose_file="$(cd "$script_dir/.." && pwd)/compose.yaml"
application_dev_file="$(cd "$script_dir/.." && pwd)/demo-service/src/main/resources/application-dev.yml"
bootstrap_file="$(cd "$script_dir/.." && pwd)/scripts/bootstrap-dev.sh"

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

require_file() {
  [[ -f "$1" ]] || fail "missing required file: $(basename "$1")"
}

require_text() {
  local file="$1"
  local pattern="$2"
  rg -q --fixed-strings "$pattern" "$file" || fail "$(basename "$file") must contain: $pattern"
}

verify_static() {
  require_file "$sql_file"
  require_file "$mongo_file"
  require_file "$account_file"
  require_file "$readme_file"
  require_file "$compose_file"
  require_file "$application_dev_file"
  require_file "$bootstrap_file"

  local expected_tables=(
    users addresses user_oauth_bind user_credit_logs user_violations user_bans
    products favorites reviews product_report_ticket product_violations product_status_audit_log
    orders order_items after_sales after_sale_evidences order_flags order_ship_timeout_task
    order_ship_reminder_task order_refund_task user_wallets wallet_transactions withdraw_requests
    points_ledger message_outbox mq_consume_log
  )
  local table
  for table in "${expected_tables[@]}"; do
    require_text "$sql_file" "CREATE TABLE \`$table\`"
  done

  local table_count
  table_count="$(rg -c '^CREATE TABLE ' "$sql_file")"
  [[ "$table_count" == "26" ]] || fail "expected 26 CREATE TABLE statements, found $table_count"

  local required_fragments=(
    'DROP DATABASE IF EXISTS `secondhand2`'
    'UNIQUE KEY `uk_orders_order_no` (`order_no`)'
    'UNIQUE KEY `uk_event_id` (`event_id`)'
    'UNIQUE KEY `uk_consumer_event` (`consumer`, `event_id`)'
    'UNIQUE KEY `uk_ship_timeout_order_id` (`order_id`)'
    'UNIQUE KEY `uk_order_level` (`order_id`, `level`)'
    'UNIQUE KEY `uk_wallet_tx_biz_type_biz_id` (`biz_type`, `biz_id`)'
    'INDEX `idx_outbox_status_retry_id` (`status`, `next_retry_time`, `id`)'
    'CONSTRAINT `chk_products_status`'
    'CONSTRAINT `chk_orders_status`'
    'ON DELETE CASCADE'
    'ON DELETE RESTRICT'
    '$2a$'
  )
  local fragment
  for fragment in "${required_fragments[@]}"; do
    require_text "$sql_file" "$fragment"
  done

  rg -q "1001, 'DEV-ORDER-PENDING-001'.*'pending'.*NOW\\(\\)" "$sql_file" || fail 'pending-order fixture must use current time to avoid timeout-job cancellation at startup'
  rg -q "1002, 'DEV-ORDER-PAID-001'.*'paid'.*DATE_SUB\\(NOW\\(\\)" "$sql_file" || fail 'paid-order fixture must use relative current time'

  if rg -n "(password|密码).*(admin123|123456)|(admin123|123456).*password" "$sql_file"; then
    fail 'plaintext demo password found in SQL seed data'
  fi

  require_text "$mongo_file" "{ orderId: 1, createTime: -1 }"
  require_text "$mongo_file" "uniq_order_clientMsg"
  require_text "$mongo_file" "idx_to_read"
  require_text "$account_file" "CREATE USER"
  require_text "$readme_file" "admin123"
  require_text "$readme_file" "123456"
  require_text "$compose_file" "rabbitmq:4-management"
  require_text "$compose_file" "redis:7.4-alpine"
  require_text "$compose_file" "mongo:8.0"
  require_text "$application_dev_file" "DEMO_MYSQL_USERNAME:secondhand_dev"
  require_text "$bootstrap_file" "00_rebuild_secondhand2.sql"

  printf 'PASS: static database rebuild contract\n'
}

mysql_query() {
  local query="$1"
  MYSQL_PWD="${MYSQL_PWD:?set MYSQL_PWD for --mysql}" mysql \
    --protocol=TCP \
    -h "${MYSQL_HOST:-127.0.0.1}" \
    -P "${MYSQL_PORT:-3306}" \
    -u "${MYSQL_USER:?set MYSQL_USER for --mysql}" \
    -N -B -e "$query"
}

verify_mysql() {
  verify_static
  local table_count
  table_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'secondhand2' AND table_type = 'BASE TABLE';")"
  [[ "$table_count" == "26" ]] || fail "live database has $table_count tables, expected 26"

  local fixture_count
  fixture_count="$(mysql_query "SELECT COUNT(*) FROM secondhand2.users WHERE mobile IN ('13900000001', '13800000001');")"
  [[ "$fixture_count" == "2" ]] || fail "expected administrator and buyer fixture accounts"

  local hash_count
  hash_count="$(mysql_query "SELECT COUNT(*) FROM secondhand2.users WHERE mobile IN ('13900000001', '13800000001') AND password LIKE '\\\$2a\\\$%';")"
  [[ "$hash_count" == "2" ]] || fail "demo account hashes are not BCrypt $2a hashes"

  local order_status_count
  order_status_count="$(mysql_query "SELECT COUNT(DISTINCT status) FROM secondhand2.orders WHERE status IN ('pending', 'paid', 'shipped', 'completed', 'cancelled');")"
  [[ "$order_status_count" == "5" ]] || fail "expected fixtures for all five order statuses"

  local required_index_count
  required_index_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'secondhand2' AND ((table_name = 'message_outbox' AND index_name = 'idx_outbox_status_retry_id') OR (table_name = 'orders' AND index_name = 'uk_orders_order_no') OR (table_name = 'wallet_transactions' AND index_name = 'uk_wallet_tx_biz_type_biz_id'));")"
  [[ "$required_index_count" == "6" ]] || fail "required MySQL indexes are missing"

  printf 'PASS: live MySQL rebuild contract\n'
}

case "${1:---static}" in
  --static) verify_static ;;
  --mysql) verify_mysql ;;
  *) fail "usage: $0 [--static|--mysql]" ;;
esac
