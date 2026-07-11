# Backup and restore

Production PostgreSQL is backed up with `ops/backup-postgres.sh`. Run it before every schema deployment and on a scheduled daily job. Backups default to `/opt/skillswap/backups`, permissions `0600`, retention 14 days.

Copy every backup to encrypted off-VPS storage. A file left only on the VPS is not a disaster-recovery backup.

Restore procedure:

1. Stop the backend and RabbitMQ consumers.
2. Create a final backup of the current database.
3. Run `ops/restore-postgres.sh <backup.dump.gz>` against an isolated restore rehearsal first.
4. Verify Flyway history, row counts, foreign keys, and critical booking/payment records.
5. Start the matching application image and run `ops/smoke-test.sh`.

Restore is a destructive operation and must never run while application traffic is active.
