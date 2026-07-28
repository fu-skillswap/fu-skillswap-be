# Release Gate

## Required GitHub environment

The beta pipeline uses only the protected `production` GitHub Actions environment. Configure a required reviewer before production deployment.

The production environment requires:

- `VPS_HOST`;
- `VPS_USER`;
- `VPS_SSH_KEY`.

There is no automatic staging restore rehearsal and no `STAGING_*` secret in the beta pipeline.

## Release procedure

The release path is:

```text
CI verify
-> security scan
-> release preflight
-> immutable image SHA
-> production approval
-> database backup
-> deploy
-> readiness and read-only smoke test
```

The release preflight validates shell syntax, Docker Compose topology, clean worktree and Flyway rollout headers. Production deploy fails before container startup when any required environment variable, image pull or backup step fails.

After a successful smoke test, deployment stores `/opt/skillswap/releases/<git-sha>.env` with the image SHA, backup reference and smoke outcome.

## Recovery

For compatible `EXPAND` migrations, prefer application-image rollback. Restore PostgreSQL only for a destructive migration or confirmed data corruption, using the guarded procedure in `operations-runbook.md`.

`ops/rehearse-migration.sh` remains available for a manually initiated non-production restore drill. It must never run against the production VPS or a production volume.
