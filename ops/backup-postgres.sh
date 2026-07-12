#!/bin/sh
set -eu

: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"

BACKUP_DIR="${BACKUP_DIR:-/opt/skillswap/backups}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
TARGET="${BACKUP_DIR}/skillswap-${TIMESTAMP}.dump"

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
docker cp "skillswap-postgres:/tmp/skillswap-${TIMESTAMP}.dump" "$TARGET"
docker exec skillswap-postgres rm -f "/tmp/skillswap-${TIMESTAMP}.dump"
gzip "$TARGET"
test -s "${TARGET}.gz"
find "$BACKUP_DIR" -type f -name 'skillswap-*.dump.gz' -mtime "+${BACKUP_RETENTION_DAYS:-14}" -delete
printf '%s\n' "Backup created: ${TARGET}.gz"
