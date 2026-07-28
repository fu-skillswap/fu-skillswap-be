#!/bin/sh
set -eu

: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"

BACKUP_DIR="${BACKUP_DIR:-/opt/skillswap/backups}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
TARGET="${BACKUP_DIR}/skillswap-${TIMESTAMP}.dump"
MANIFEST="${BACKUP_DIR}/skillswap-${TIMESTAMP}.manifest"

if ! docker ps -a --format '{{.Names}}' | grep -q "^skillswap-postgres$"; then
  echo "Container skillswap-postgres does not exist yet. Skipping backup for first deployment."
  exit 0
fi

if ! docker ps --format '{{.Names}}' | grep -q "^skillswap-postgres$"; then
  echo "Container skillswap-postgres exists but is not running. Refusing to deploy without a backup."
  exit 1
fi

mkdir -p "$BACKUP_DIR"
umask 077
docker exec skillswap-postgres pg_dump \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --format custom \
  --no-owner \
  --file "/tmp/skillswap-${TIMESTAMP}.dump"
# Verify the custom dump inside the PostgreSQL image before it leaves the host.
docker exec skillswap-postgres pg_restore --list "/tmp/skillswap-${TIMESTAMP}.dump" >/dev/null
docker cp "skillswap-postgres:/tmp/skillswap-${TIMESTAMP}.dump" "$TARGET"
docker exec skillswap-postgres rm -f "/tmp/skillswap-${TIMESTAMP}.dump"
gzip "$TARGET"
test -s "${TARGET}.gz"
SHA256="$(sha256sum "${TARGET}.gz" | awk '{print $1}')"
BYTES="$(wc -c < "${TARGET}.gz" | tr -d ' ')"
umask 077
cat > "$MANIFEST" <<EOF
created_at_utc=${TIMESTAMP}
backup_file=${TARGET}.gz
sha256=${SHA256}
bytes=${BYTES}
postgres_database=${POSTGRES_DB}
format=pg_dump_custom_gzip
verified_pg_restore_list=true
EOF
printf '%s  %s\n' "$SHA256" "$(basename "${TARGET}.gz")" > "${TARGET}.gz.sha256"
chmod 600 "${TARGET}.gz" "$MANIFEST" "${TARGET}.gz.sha256"
find "$BACKUP_DIR" -type f -name 'skillswap-*.dump.gz' -mtime "+${BACKUP_RETENTION_DAYS:-14}" -delete
find "$BACKUP_DIR" -type f \( -name 'skillswap-*.manifest' -o -name 'skillswap-*.dump.gz.sha256' \) -mtime "+${BACKUP_RETENTION_DAYS:-14}" -delete
printf '%s\n' "Backup created: ${TARGET}.gz"
printf '%s\n' "BACKUP_FILE=${TARGET}.gz"
printf '%s\n' "BACKUP_MANIFEST=${MANIFEST}"
