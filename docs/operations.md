# SkillSwap Operations

This is the only backend operations reference. Runtime code, Compose files and
`ops/` scripts remain the authoritative implementation.

## Production topology

One VPS runs PostgreSQL, RabbitMQ and the Spring Boot container through:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml
```

PostgreSQL and the backend bind only to loopback. The host reverse proxy owns
public HTTP/HTTPS access. RabbitMQ is not publicly exposed. It runs the STOMP
plugin for browser WebSocket relay and the durable realtime fanout queues only;
email, Google Calendar, Bunny and payment workers remain PostgreSQL-backed jobs.

## First setup

1. Install Docker Engine with Compose V2 on the VPS.
2. Create `/opt/fu-skillswap/backend` and place a production `.env` there.
3. Copy `.env.example`; replace every placeholder secret; set file permission
   to `0600`.
4. Configure GitHub production environment secrets: `VPS_HOST`, `VPS_USER`, and
   `VPS_SSH_KEY`.
5. If the GHCR package is private, authenticate Docker on the VPS with a token
   that has `read:packages`.

The production `.env` must provide database, RabbitMQ, JWT, cursor encryption,
CORS and other enabled-provider values. `APP_IMAGE` is injected by CI and must
always be an immutable SHA image, never `latest`.

For the full launch, keep `REALTIME_OUTBOX_ENABLED=true` and
`WEBSOCKET_STOMP_ENABLED=true` together. Compose mounts
`ops/rabbitmq/enabled_plugins`, which enables `rabbitmq_stomp`; the RabbitMQ
healthcheck verifies both the broker and the plugin before the backend starts.

## Release path

```text
git push main
-> CI verify and security scan
-> release preflight and migration policy check
-> immutable GHCR image
-> production approval
-> backup
-> deploy, readiness and read-only smoke test
```

Every new Flyway migration must declare `-- rollout: EXPAND` or
`-- rollout: CONTRACT` in its first eight lines. Normal releases only accept
compatible `EXPAND` changes. Run locally before pushing:

```bash
sh ops/release-preflight.sh
sh scripts/verify-migration-policy.sh
```

## Daily checks

```bash
cd /opt/fu-skillswap/backend
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs --tail=200 spring-backend
free -h
df -h
```

Readiness includes the database and RabbitMQ. Treat persistent restart loops,
memory pressure, low disk space, or a failed readiness check as deployment
incidents before accepting new traffic.

## Backup and recovery

The deployment pipeline runs `ops/backup-postgres.sh` before a release. Keep an
encrypted copy off the VPS and periodically verify a restore outside production.

For a compatible migration failure, roll back to the preceding immutable image.
Restore PostgreSQL only for data corruption or destructive schema work. A
production restore is deliberately guarded and requires drained traffic, a
stopped backend, `PRODUCTION_TRAFFIC_DRAINED=true`, and
`SKILLSWAP_RESTORE_CONFIRM=RESTORE_PRODUCTION`.

Never run `docker compose down -v` in production.
