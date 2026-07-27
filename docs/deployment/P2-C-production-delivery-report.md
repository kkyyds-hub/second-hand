# P2-C Production Deployment and Final Delivery Report

Start SHA: `276cfb9eea9ad15b7f583f9e685fbc1e56ec4674`.

## Completed evidence

- The isolated MySQL 8.4 migration replay passed all four required scenarios: old `OK/FAIL` constraint upgrade, missing constraint creation, legacy `PROCESSING` conversion to `FAIL` with the prescribed error, and idempotent second execution. The temporary database and container were removed.
- The existing `MqConsumeGuardIntegrationTest` passed its `RETRYABLE_FAILED` path as part of the backend suite.
- `application-prod.yml` requires production data-service settings and JWT/upload/payment secrets through environment variables. `ProductionSecretsValidator` fails closed in the `prod` Profile when required secrets are absent or equal to known development values. It uses `/data/uploads`, disables mapper debug, email preview storage and activation-link logging, and keeps payment explicitly mock-only.
- Production Compose defines MySQL, Redis, MongoDB, RabbitMQ, migration, backend, user web, and admin web services on an internal network. Only user port `80`, admin port `8081`, and loopback RabbitMQ management port `15672` are published. Named volumes persist MySQL, Redis, MongoDB, RabbitMQ, and uploads; JSON logs rotate at 10 MiB x3.
- The user and admin Nginx images use `/api` and `/uploads` reverse proxies, SPA history fallback, cache controls, and baseline browser security headers. Admin build sets `VITE_USE_MOCK=false`.
- Empty-volume schema initialization is `database/production/00_schema.sql`; optional fixture data is separated into `90_demo_seed.sql` and only available in the `demo` Compose profile with an explicit enable variable.
- Deployment, stop, status, smoke-test, backup, and documented restore procedures are provided under `scripts/prod/` and `docs/deployment/`.

## Validation

- `docker compose -f compose.production.yml --env-file /tmp/second-hand-p2-c/.env.acceptance config`: passed.
- `git diff --check`: passed after the deployment files were assembled.
- `mvn -pl demo-service -am test`: passed, 127 tests.
- `mvn -pl demo-service -am -DskipTests package`: passed.
- User and admin `npm ci`, `npm run build`, and `npm run build:real`: passed. Existing npm audit reports seven dependency vulnerabilities and was not modified in this deployment-only change.
- A direct `prod` startup with a required secret absent failed during context initialization through `ProductionSecretsValidator`.

## Environment block

The required fresh Compose acceptance could not start. Docker failed before creating project containers because this host cannot connect to `auth.docker.io:443` to fetch uncached `maven`, JRE, Node, and Nginx image metadata. A direct curl connection check also timed out. Consequently Docker image build, fresh-stack service status, browser transaction/admin acceptance, proxy runtime checks, uploads, restart persistence, logs, and cleanup of a created acceptance stack remain unverified.

## Boundaries

No marketplace business rules, payment state machine, real payment provider, SMS provider, or MQ topology were changed. Payment remains mock-only and is not suitable for real funds or goods trading. MQ immediate requeue and manual DLQ redrive remain operational boundaries.

Final Gate: `ENV_BLOCKED`.
