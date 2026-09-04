# MVP Deployment Baseline

This branch (`release/mvp-baseline`) is the deployable baseline. The paused Modulith work is preserved on `main` at checkpoint `54575d62`.

## Required environment

Set these variables before starting the application:

- `DATABASE_URL` (PostgreSQL JDBC URL)
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_ISSUER`
- `JWT_AUDIENCE`
- `CORS_ALLOWED_ORIGIN_PATTERNS`
- `CURSOR_AES_KEY`
- `CURSOR_HMAC_KEY`

Use `SPRING_PROFILES_ACTIVE=prod` and provide any provider credentials documented in `.env.example`.

`CORS_ALLOWED_ORIGIN_PATTERNS` is required in the production environment and must
contain only the specific HTTPS frontend origin(s). Do not use `localhost`,
`127.0.0.1`, or `*` in the production `.env`; `ops/production-preflight.sh`
rejects those values. Local development uses the `dev` profile, whose defaults
allow the local frontend origins.

## Startup

Build and start with:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

The application listens on port `8080`.

## Database order

1. Ensure PostgreSQL is reachable and backups are available.
2. Start the application with Flyway enabled (`FLYWAY_ENABLED=true`).
3. Keep `HIBERNATE_DDL_AUTO=validate`; do not use `update` in production.
4. Confirm Flyway completes before accepting traffic.

## Health checks

- `GET /health`
- `GET /actuator/health`

Both endpoints are unauthenticated for load balancers and reverse proxies.

## Rollback

1. Stop the current container and route traffic away.
2. Redeploy the previous image tag.
3. Restore the database only if the release included an incompatible migration and rollback is approved.
4. Verify both health endpoints before restoring traffic.

## Known limitations

- The paused Modulith refactor remains on `main`; it is not included in this release branch.
- The refactor branch currently has test-compilation failures and must not be deployed.
- Docker image verification requires a running Docker daemon.
