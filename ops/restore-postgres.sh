#!/bin/sh
set -eu

: "${1:?Usage: restore-postgres.sh /path/to/backup.dump.gz}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${SKILLSWAP_RESTORE_CONFIRM:?Set SKILLSWAP_RESTORE_CONFIRM=RESTORE_PRODUCTION after draining traffic and stopping the backend}"
: "${PRODUCTION_TRAFFIC_DRAINED:?Set PRODUCTION_TRAFFIC_DRAINED=true after the reverse proxy has stopped new traffic}"

if [ "$SKILLSWAP_RESTORE_CONFIRM" != "RESTORE_PRODUCTION" ] || [ "$PRODUCTION_TRAFFIC_DRAINED" != "true" ]; then
  echo "Refusing destructive restore without explicit production confirmation and drained traffic." >&2
  exit 2
fi

if docker ps --format '{{.Names}}' | grep -qx 'skillswap-backend'; then
  echo "Refusing restore while skillswap-backend is running. Stop the backend first." >&2
  exit 2
fi

BACKUP_FILE="$1"
test -f "$BACKUP_FILE"
if [ -f "${BACKUP_FILE}.sha256" ]; then
  (cd "$(dirname "$BACKUP_FILE")" && sha256sum -c "$(basename "${BACKUP_FILE}").sha256")
fi
TEMP_DUMP="$(mktemp)"
trap 'rm -f "$TEMP_DUMP"' EXIT
gzip -dc "$BACKUP_FILE" > "$TEMP_DUMP"
docker cp "$TEMP_DUMP" skillswap-postgres:/tmp/skillswap-restore.dump
docker exec skillswap-postgres pg_restore --list /tmp/skillswap-restore.dump >/dev/null
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
