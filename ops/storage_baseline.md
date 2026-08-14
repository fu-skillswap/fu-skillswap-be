# Storage Baseline & Retention Policy

**Updated:** 2026-08-14
**Project:** fu-skillswap-be

## 1. Canonical storage policy

- **Database:** PostgreSQL 16+ on the VPS.
- **VPS disk:** 100 GB SSD baseline. Warning at 60%, retention/archive action at 70%, emergency protection at 85%.
- **Safety floor:** always keep at least 15 GB free. The emergency guard is triggered when either the percentage or free-space floor is breached.
- **Object storage:** Cloudflare R2 through `S3StorageGatewayImpl` for durable archives, uploaded files and database backups.
- **Local fallback:** `LocalFileStorageGatewayImpl` is allowed only on `local` and `test` profiles; production fails fast if R2 is unavailable.

The database remains the source of truth for active transactional data. R2 is the source of truth for archived payloads and backup copies. No cleanup job may delete PostgreSQL data before the R2 upload, checksum/size validation, row-count validation and manifest write have succeeded.

## 2. Backup policy

`ops/backup-postgres.sh` is the canonical backup job. `pg_dump -Fc` creates a private local backup, verifies it with `pg_restore --list`, uploads it to the R2 prefix `db_backups/`, then verifies the remote object size and SHA-256 metadata.

- **R2 retention:** 30 days, enforced by the Cloudflare R2 lifecycle rule.
- **Local retention:** latest 3 verified backups maximum, with a configurable local cap of 5 GB (`LOCAL_BACKUP_MAX_BYTES`). Older local files are removed only after the corresponding R2 object has been verified.
- **Failure rule:** if upload or remote verification fails, the local backup is retained and the job exits non-zero.
- **Restore:** `ops/db_restore_test.sh` must periodically restore a recent R2 backup into an isolated database. A successful upload alone is not proof that a restore works.
- **Single policy:** the former 14-day local age policy is removed. Local copies are a short recovery cache; R2 is the 30-day retention store.

## 3. Archive and cleanup retention

| Dataset | Hot PostgreSQL policy | Cold/R2 or deletion policy |
|---|---:|---|
| `internal_telemetry_events` | 14 days | JSONL.GZ archive to R2, then delete after verification |
| `audit_logs` | 90 days | JSONL.GZ archive to R2, then delete after verification |
| Read `notifications` | 120 days | Per-user JSONL.GZ archive + manifest to R2, then delete |
| Unread `notifications` | 240 days | Hard delete; no active notification is deleted |
| `user_sessions` EXPIRED/REVOKED | 30 days | Hard delete; ACTIVE sessions are never deleted |
| `chat_attachments` | 90 days | `ACTIVE -> EXPIRED`; after 7-day grace, delete R2 object and mark metadata `DELETED` |
| `email_outbox` terminal | 7 days | Hard delete |
| `course_outbox_events` terminal | 7 days | Hard delete |
| `bunny_webhook_events` terminal | 7 days | Hard delete |
| `messages` | KEEP for current scale | No archive reader exists yet; monitor size and bloat |

Archive object names are deterministic and contain a content checksum. `notification_archive_manifests` records the user, period, R2 key, checksum, row count and creation time. Financial/commerce data is never archived or hard-deleted by these jobs: bookings, payment orders, credit ledger entries, settlement entries, payout requests and course enrollments remain in PostgreSQL.

## 4. Disk guard and operational monitoring

Run `ops/storage_guard.sh` from cron or the deployment scheduler. It reports:

- root filesystem usage and free-space floor;
- PostgreSQL and RabbitMQ Docker volume sizes;
- Docker storage usage;
- local backup directory size.

Exit levels:

- `0 NORMAL`: below 60% and at least 15 GB free;
- `1 WARN`: at least 60%; prepare review;
- `2 ARCHIVE_CLEANUP`: at least 70%; run reviewed retention/archive jobs;
- `3 EMERGENCY`: at least 85% or below 15 GB free; protect writes and page the operator.

The guard is intentionally read-only. It does not silently delete business data. Cleanup remains bounded, auditable and dataset-specific.

Run `ops/rabbitmq_storage_watch.sh` for queue depth, ready/unacknowledged messages, consumer count and queue state. Durable DLQ/DLX handling remains enabled. TTL is allowed only for explicitly ephemeral realtime events; booking/payment events must remain durable until processed.

Run `psql -f ops/postgres_storage_watch.sql` for relation size, table/index/TOAST size, dead tuples, autovacuum and analyze timestamps. Do not run `VACUUM FULL` during normal service hours. Use a maintenance window or `pg_repack` when table rewrite is justified.

## 5. Future partition design (not implemented)

When volume justifies it, partition `messages`, `notifications`, `audit_logs` and `internal_telemetry_events` by time (monthly or quarterly) while preserving the existing logical tables and archive reader contract. The migration must be planned as an expand/contract rollout with backfill, dual-read verification, detach/archive, and restore tests. No partition migration is included in this change.

## 6. Deployment checklist

- [x] Backup local cleanup: keep latest 3 only after R2 verification and enforce local byte cap.
- [x] Disk usage guard: 60% warning, 70% archive/cleanup action, 85% emergency, 15 GB free floor.
- [x] `user_sessions` cleanup: EXPIRED/REVOKED older than 30 days; ACTIVE excluded; indexed and idempotent.
- [x] Notification cleanup: read records archived after 120 days with manifest/checksum/count; unread records deleted after 240 days.
- [x] Chat attachment cleanup: 90-day expiry, 7-day grace, R2 deletion before `DELETED` metadata transition.
- [x] RabbitMQ monitoring: queue depth and consumer state; no unsafe TTL on commerce events; DLQ policy documented.
- [x] PostgreSQL storage monitoring: relation/index/dead-tuple/autovacuum report and targeted autovacuum settings.
- [ ] Restore rehearsal: verify a recent R2 backup in an isolated PostgreSQL instance.
- [ ] Production cron/alert wiring: schedule guard and monitoring scripts with operator alerts.
- [ ] R2 lifecycle verification: confirm 30-day rule for `db_backups/` and archive prefixes.
