#!/bin/sh
set -eu

: "${1:?Usage: restore-postgres.sh /path/to/backup.dump.gz}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"

BACKUP_FILE="$1"
test -f "$BACKUP_FILE"
TEMP_DUMP="$(mktemp)"
trap 'rm -f "$TEMP_DUMP"' EXIT
gzip -dc "$BACKUP_FILE" > "$TEMP_DUMP"
docker cp "$TEMP_DUMP" skillswap-postgres:/tmp/skillswap-restore.dump
docker exec skillswap-postgres pg_restore \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --clean \
  --if-exists \
  --no-owner \
  --exit-on-error \
  /tmp/skillswap-restore.dump
docker exec skillswap-postgres rm -f /tmp/skillswap-restore.dump
printf '%s\n' "Restore completed from: ${BACKUP_FILE}"
