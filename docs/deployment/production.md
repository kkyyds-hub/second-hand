# Production deployment

This Compose stack is for a controlled demonstration environment. Payment is mock-only; it does not connect to real funds and must not be used for real goods trading.

## First deployment

1. Copy `.env.production.example` to `.env.production`, replace every placeholder with unique values, and keep that file outside Git.
2. Run `bash scripts/prod/deploy.sh`.
3. User traffic is served on `http://host/`; the separate administration UI is on `http://host:8081/`. RabbitMQ management, if needed, binds only to `127.0.0.1:15672`.

An empty MySQL volume runs `database/production/00_schema.sql` once. It creates only tables, indexes, constraints, and no accounts or business fixtures. `database/production/90_demo_seed.sql` is not automatic; use the Compose `demo` profile only with `SECONDHAND_DEMO_SEED_ENABLED=true` for a disposable demonstration environment.

## Existing database upgrade

1. Stop backend consumers: `bash scripts/prod/stop.sh`.
2. Back up MySQL, MongoDB, and uploads: `bash scripts/prod/backup.sh /absolute/backup-directory`.
3. Start with `bash scripts/prod/deploy.sh`. The one-shot `migration` service applies versioned, idempotent migrations before backend startup.
4. Run `bash scripts/prod/smoke-test.sh`; inspect `docker compose -f compose.production.yml --env-file .env.production logs --tail=200`.

Never run `database/00_rebuild_secondhand2.sql` against an existing database. Do not use `docker compose down -v` for normal deployments.

## Operations

`scripts/prod/status.sh` shows service state. `scripts/prod/stop.sh` stops containers but preserves all named volumes. Compose rotates JSON logs at 10 MiB with three files per container.

The backend runs as a non-root user, stores uploads in the persistent `/data/uploads` volume, and serves them under `/uploads/**`. Both Nginx containers proxy `/api/` and `/uploads/` to the internal backend and provide SPA fallback for deep links. Database and broker ports are internal-only.

The mock payment mode is explicitly controlled by `SECONDHAND_PAYMENT_MODE=mock`; no real payment provider is configured. MQ retries use immediate requeue behavior and manual DLQ redrive remains an operational procedure rather than an automatic recovery guarantee.
