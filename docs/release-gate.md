# Release gate and staging rehearsal

## Required GitHub environments

Create protected `staging` and `production` environments in GitHub Actions. Configure required reviewers for `production`; `staging` may deploy automatically after CI succeeds.

Required staging secrets:

- `STAGING_VPS_HOST`
- `STAGING_VPS_USER`
- `STAGING_VPS_SSH_KEY`
- `STAGING_REHEARSAL_BACKUP_FILE`: absolute path on the staging VPS to an ephemeral decrypted, production-like or sanitized `pg_dump` custom archive.

The staging VPS stores its own `.env` at `/opt/fu-skillswap/staging-rehearsal/.env`. It must contain database, RabbitMQ, JWT and cursor keys only for staging. The rehearsal always overrides mail, storage, OAuth and PayOS/webhook configuration, and starts the candidate with `APPLICATION_SCHEDULING_ENABLED=false`, so it cannot call production providers or process durable jobs.

## Rehearsal procedure

The pipeline pulls the immutable image SHA, invokes `ops/rehearse-migration.sh`, then preserves evidence under `/opt/skillswap-staging/release-evidence/<run-id>/`:

- manifest with image SHA, backup checksum, restore duration, candidate Flyway/startup duration and timestamps;
- Flyway history before and after candidate startup;
- counts for users, bookings, payment orders, ledger and outbox;
- read-only smoke output.

The rehearsal uses uniquely named containers, network and volume. It never uses `skillswap-postgres`, production compose files, production ports or production volumes. Containers and volume are removed on success or failure. Backup transport/storage encryption is an operator responsibility; the decrypted copy on staging must be `0600`. The workflow sets `REHEARSAL_DELETE_INPUT_BACKUP=true`, so it must never point to the durable encrypted source and is removed after the drill.

## Production deploy

Production starts only after the staging job and required environment approval pass. The deploy records `/opt/skillswap/releases/<git-sha>.env` after smoke succeeds. Use this manifest and deploy log to choose an application-only rollback or the guarded restore procedure in `operations-runbook.md`.
