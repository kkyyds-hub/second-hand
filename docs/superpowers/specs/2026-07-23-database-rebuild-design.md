# Secondhand Platform Database Rebuild Design

## Goal

Rebuild the project's local development data stores from source-controlled scripts.
The rebuild must support the existing Spring Boot/MyBatis application without
changing its table names, mapped columns, status values, or business keys.

The MySQL rebuild is intentionally destructive and repeatable. It drops and
recreates the `secondhand2` database, creates all relational structures, then
loads representative demo data. MongoDB initialization separately prepares the
`demo.order_messages` collection used by the in-app message service.

## Constraints

- Target: MySQL 8.4, InnoDB, `utf8mb4`.
- MySQL startup script is for development only and clears all existing data.
- Passwords in demo rows use BCrypt hashes. No plaintext password is stored.
- The backend identifies an administrator as a `users` row whose username
  begins with `admin`; no unused administrator table is introduced.
- MongoDB messages remain outside MySQL because the application uses
  `MongoTemplate` and `MongoRepository` for them.
- The project must not store a MySQL administrator password. A separate,
  optional development application-account script is kept apart from schema
  creation.

## Delivery Files

`database/00_rebuild_secondhand2.sql`

- Prints a development-only warning.
- Drops and recreates `secondhand2`.
- Creates tables in dependency order, followed by indexes, foreign keys,
  check constraints, and demo data.
- Uses transaction boundaries for seed data where MySQL DDL behavior permits.

`database/01_seed_mongodb.js`

- Connects to database `demo`.
- Recreates the `order_messages` demo collection and indexes.
- Inserts representative buyer/seller/system messages with `clientMsgId`
  values for message idempotency.

`database/02_create_dev_app_user.sql`

- Optional privileged setup script for a local JDBC application account.
- Is deliberately separate from the destructive rebuild and from application
  configuration, so the MySQL administrator credential is never committed.

`database/README.md`

- Documents prerequisites, execution order, demo accounts, destructive scope,
  and the matching application configuration variables.

## Relational Model

### Account and Credit

| Table | Purpose |
| --- | --- |
| `users` | Accounts, BCrypt password, profile, seller flag, account state, credit score and soft delete flag. |
| `addresses` | User-owned shipping addresses and one default address per user in application logic. |
| `user_oauth_bind` | Third-party identity bindings. |
| `user_credit_logs` | Immutable credit score changes. |
| `user_violations` | Risk and policy violations keyed by business action. |
| `user_bans` | User-ban history and active-ban state. |

### Product and Governance

| Table | Purpose |
| --- | --- |
| `products` | Seller-owned listing, price, category, images, stock, status and soft delete flag. |
| `favorites` | User-to-product favorite relation with soft delete. |
| `reviews` | Buyer and seller order reviews. |
| `product_report_ticket` | Product report lifecycle. |
| `product_violations` | Product policy violations and disposition. |
| `product_status_audit_log` | Immutable status transition audit trail. |

### Orders, After-sales, and Scheduled Work

| Table | Purpose |
| --- | --- |
| `orders` | Order header, buyer/seller, money, status, shipment snapshot, timestamps and cancellation reason. |
| `order_items` | Product and price snapshot for an order. |
| `after_sales` | One after-sale claim per order. |
| `after_sale_evidences` | Ordered evidence images attached to an after-sale claim. |
| `order_flags` | Administrator or system risk flags on orders. |
| `order_ship_timeout_task` | One ship-timeout compensation task per order. |
| `order_ship_reminder_task` | H24/H6/H1 seller-reminder tasks. |
| `order_refund_task` | Idempotent refund compensation tasks. |

### Assets and Asynchronous Processing

| Table | Purpose |
| --- | --- |
| `user_wallets` | One balance row per user. |
| `wallet_transactions` | Immutable, idempotent wallet ledger. |
| `withdraw_requests` | Withdrawal requests and lifecycle. |
| `points_ledger` | Immutable, idempotent points ledger. |
| `message_outbox` | Transactional outbox events and retry state. |
| `mq_consume_log` | Consumer-side event deduplication records. |

## Relationships and Delete Rules

- A user owns products, addresses, favorites, wallets, credit records,
  violations, bans, and OAuth bindings.
- An order references a buyer, a seller, and one or more order items. An order
  may have one after-sale claim, many flags/reminders/refund attempts, and one
  ship-timeout task.
- A product is referenced by favorites, order items, reviews, reports,
  violations, and status audits.
- `order_items` and `after_sale_evidences` cascade from their direct parent.
- Financial, governance, audit, outbox, consumer-log, and task records use
  restrictive deletion semantics. The application preserves their evidence
  rather than deleting history.
- `users`, `products`, and `favorites` retain their existing soft-delete
  behavior.

## Integrity and Performance Rules

Natural and idempotency keys include:

- `users`: username, mobile, email.
- `user_oauth_bind`: provider plus external identity.
- `favorites`: user plus product.
- `orders`: order number.
- `reviews`: order plus reviewer role.
- `after_sales`: order.
- `order_flags`: order plus flag type.
- `points_ledger`: user plus business type plus business id.
- `wallet_transactions`: business type plus business id.
- `message_outbox`: event id.
- `mq_consume_log`: consumer plus event id.
- Scheduled tasks: the existing order/type/level/idempotency business keys.

The schema adds the existing mapper-driven indexes for product discovery,
buyer and seller order lists, governance queues, outbox scans, task retries,
address lists, and audit histories. Status checks preserve the values currently
used by the application: product `under_review/on_sale/off_shelf/sold`, order
`pending/paid/shipped/completed/cancelled`, outbox `NEW/SENT/FAIL`, and the
documented after-sale and task lifecycles.

## Demo Dataset

The seed data must include:

- Administrator: mobile `13900000001`, password `admin123`.
- Buyer: mobile `13800000001`, password `123456`.
- At least one seller and several products in review, on-sale, off-shelf, and
  sold states.
- Pending, paid, shipped, completed, and cancelled orders.
- A completed review pair, an active after-sale with evidence, wallet and
  points ledger rows, a withdrawal request, report/violation/audit rows, and
  representative outbox/MQ/task records.
- MongoDB message samples for buyer-seller conversation and system notices.

All contact data, addresses, product descriptions, tracking numbers, and
event payloads are fictional development fixtures.

## Validation

Validation after implementation will confirm:

1. The MySQL script can run twice with the same result.
2. All 26 relational tables, constraints, and indexes exist after rebuild.
3. Demo user and administrator credentials authenticate through existing
   BCrypt login flows.
4. The primary product, order, wallet, after-sale, governance, and task mapper
   queries run without a missing-table or missing-column error.
5. The MongoDB script creates the expected collection, indexes, and messages.
6. The scripts never require a committed MySQL administrator password.
