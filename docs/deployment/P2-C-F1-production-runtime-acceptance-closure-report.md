# P2-C-F1 Production Runtime Acceptance Closure Report

Start SHA: `305320b8944bc9ccddca2960f05b9fc5f4b137ae`.

## Scope

This is a local production-style Compose acceptance record. Server deployment and
the full browser business acceptance were deferred by the user before this
commit. They are not represented as passed production delivery work.

## Local acceptance evidence

| Item | Status | Evidence |
| --- | --- | --- |
| 1. Start SHA | Pass | `305320b8944bc9ccddca2960f05b9fc5f4b137ae` on `main` and `origin/main` before edits. |
| 2. Original smoke route defect | Pass | The old smoke test requested `/healthz`, which the SPA proxy does not route to the backend. |
| 3. Health endpoints | Pass | Smoke test uses `/api/healthz` and `/api/readyz` through both user and admin proxies. |
| 4. Liveness | Pass | `/healthz` returns only `{"status":"UP"}`. |
| 5. Readiness | Pass | `/readyz` returns 200 only after all four dependencies respond, otherwise a sanitized 503 component name. |
| 6. MySQL readiness | Pass | `SELECT 1` is executed by the readiness handler. |
| 7. Redis readiness | Pass | A Redis connection `PING` is executed by the readiness handler. |
| 8. Mongo readiness | Pass | MongoDB `ping` is executed by the readiness handler. |
| 9. RabbitMQ readiness | Pass | A RabbitTemplate channel is opened and checked. |
| 10. Backend egress | Pass | Backend is attached to `internal` and `edge`; data services remain internal-only. |
| 11. SMTP TCP | Pass | Backend resolved `smtp.qq.com` and opened a TCP connection to port 465 without sending mail. |
| 12. User health | Pass | User proxy returned UP for liveness and readiness. |
| 13. Admin health | Pass | Admin proxy returned UP for liveness and readiness. |
| 14. Per-service state | Pass | mysql, redis, mongodb, rabbitmq, backend, user-web, and admin-web were running, healthy, and had zero restarts. |
| 15. Migration exit | Pass | `migration` exited with status 0; `demo-seed` was absent. |
| 16. Docker Hub connectivity | Pass | Required image pulls completed through the local `127.0.0.1:7890` proxy. |
| 17. Image builds | Pass | Backend, user-web, and admin-web images were built and run locally. |
| 18. Fresh Compose startup | Pass | Fresh `secondhand-p2c-f1-acceptance` volumes and project were started successfully. |
| 19. Production schema completeness | Partial | 27 tables, `mq_consume_log` lease columns, and `PROCESSING` check constraint were verified; real business-entity creation was deferred. |
| 20. User browser transaction closure | Not started | Deferred with server/browser acceptance scope. |
| 21. Admin browser closure | Not started | Deferred with server/browser acceptance scope. |
| 22. Console and page errors | Not started | Deferred with browser acceptance scope. |
| 23. HTTP 5xx observation | Partial | Proxy health and page requests passed; browser workflow-wide observation was deferred. |
| 24. Uploaded avatar | Not started | Deferred with browser acceptance scope. |
| 25. Restart persistence | Partial | Services and named volumes survived `docker compose restart`; no deferred business fixture was present. |
| 26. Down/up persistence | Partial | `docker compose down` followed by `up -d`, without `-v`, preserved schema and passed smoke. |
| 27. MySQL backup/restore | Pass | A nonempty SQL backup restored to independent `secondhand_restore_check` with 27 tables. |
| 28. Mongo backup/restore | Partial | A nonempty archive restored to an independent database; source acceptance data had zero Mongo collections. |
| 29. Upload backup/restore | Partial | Upload volume backup was created; no avatar fixture existed for an access check. |
| 30. Modified files | Pass | Compose, health endpoint, Nginx, deployment scripts, environment example, focused test, and this report only. |
| 31. Tests | Pass | `mvn -pl demo-service -am test`: 129 tests, zero failures. |
| 32. Backend package | Pass | `mvn -pl demo-service -am -DskipTests package` passed. |
| 33. Frontend builds | Pass | User and admin `npm ci`, `build`, and `build:real` passed. |
| 34. Commit SHA | Pending | Recorded in the delivery response after the single commit is created. |
| 35. Parent SHA | Pass | Required parent is the start SHA above. |
| 36. Push | Pending | Performed after final verification and single commit creation. |
| 37. Final repository state | Pending | Recorded after push and clean-worktree verification. |
| 38. Remaining defects | Deferred | No runtime defect found in the completed local scope; deferred browser and server acceptance remain. |
| 39. Mock payment boundary | Unchanged | Payment remains mock-only and must not handle real funds. |
| 40. MQ boundary | Unchanged | Immediate requeue and manual DLQ redrive remain operational procedures. |
| 41. Final gate | Partial | Local Compose readiness, network, health, build, restart, and backup checks passed; server deployment and complete browser business acceptance are intentionally not claimed. |

## Commands executed

```bash
docker compose -p secondhand-p2c-f1-acceptance -f compose.production.yml \
  --env-file /tmp/second-hand-p2-c-f1/.env.acceptance up -d --build
SECONDHAND_COMPOSE_PROJECT=secondhand-p2c-f1-acceptance \
  bash scripts/prod/smoke-test.sh /tmp/second-hand-p2-c-f1/.env.acceptance
docker compose -p secondhand-p2c-f1-acceptance -f compose.production.yml \
  --env-file /tmp/second-hand-p2-c-f1/.env.acceptance restart
docker compose -p secondhand-p2c-f1-acceptance -f compose.production.yml \
  --env-file /tmp/second-hand-p2-c-f1/.env.acceptance down
docker compose -p secondhand-p2c-f1-acceptance -f compose.production.yml \
  --env-file /tmp/second-hand-p2-c-f1/.env.acceptance up -d
```

The local acceptance stack remains running under project
`secondhand-p2c-f1-acceptance` with its named volumes intact.
