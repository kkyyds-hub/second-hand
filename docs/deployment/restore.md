# Restore procedure

1. Put the application into maintenance mode and stop all writers with `bash scripts/prod/stop.sh`.
2. Preserve the current volumes before overwriting them.
3. Restore MySQL first: import the matching `mysql-*.sql` into the target database.
4. Restore MongoDB second with `mongorestore --archive=...` using the root credentials from `.env.production`.
5. Restore the matching uploads directory to the persistent backend `/data/uploads` volume, preserving ownership readable by the non-root backend user.
6. Start with `bash scripts/prod/deploy.sh`; migrations are idempotent and will run before the backend.
7. Run `bash scripts/prod/smoke-test.sh`, verify a known user/order and uploaded asset, and check backend logs for migration or dependency errors.

Use backups created from the same logical point in time where possible. RabbitMQ queue messages are short-lived runtime state; restore exported broker definitions separately if topology has changed.
