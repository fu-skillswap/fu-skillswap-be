# Production rollback

1. Stop new traffic at the reverse proxy.
2. Stop the backend container and preserve logs.
3. If the migration is backward-compatible, deploy the previous immutable image tag without restoring the database.
4. If the migration is destructive, drain traffic, stop the backend, then restore using `PRODUCTION_TRAFFIC_DRAINED=true SKILLSWAP_RESTORE_CONFIRM=RESTORE_PRODUCTION ops/restore-postgres.sh <backup.dump.gz>`.
5. Start PostgreSQL, RabbitMQ, then backend; require readiness to become healthy.
6. Run `ops/smoke-test.sh`, then auth, booking, payment and queue smoke tests with production-safe test accounts.
7. Re-enable traffic only after reconciliation succeeds.

Never use `docker compose down -v` in production.
