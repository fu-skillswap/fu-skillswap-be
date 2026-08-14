#!/bin/bash
set -eu

: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"

BACKUP_DIR="${BACKUP_DIR:-/opt/skillswap/backups}"
BACKUP_BUCKET="${BACKUP_BUCKET:-}"
R2_ENDPOINT="${R2_ENDPOINT:-}"
R2_BACKUP_PREFIX="${R2_BACKUP_PREFIX:-db_backups}"
LOCAL_BACKUP_KEEP_COUNT="${LOCAL_BACKUP_KEEP_COUNT:-3}"
LOCAL_BACKUP_MAX_BYTES="${LOCAL_BACKUP_MAX_BYTES:-5368709120}"
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

if [ -z "$BACKUP_BUCKET" ] || [ -z "$R2_ENDPOINT" ] || [ -z "${AWS_ACCESS_KEY_ID:-}" ] || [ -z "${AWS_SECRET_ACCESS_KEY:-}" ]; then
  echo "R2 backup configuration is missing. Local backup is retained: ${TARGET}.gz" >&2
  exit 1
fi
command -v aws >/dev/null 2>&1 || { echo "aws-cli is required for R2 backup" >&2; exit 1; }

OBJECT_KEY="${R2_BACKUP_PREFIX%/}/$(basename "${TARGET}.gz")"
aws s3 cp "${TARGET}.gz" "s3://${BACKUP_BUCKET}/${OBJECT_KEY}" \
  --endpoint-url "$R2_ENDPOINT" \
  --metadata "sha256=${SHA256},bytes=${BYTES},format=pg_dump_custom_gzip"

# Do not remove the local copy until R2 confirms both object size and checksum metadata.
REMOTE_SIZE="$(aws s3api head-object --bucket "$BACKUP_BUCKET" --key "$OBJECT_KEY" \
  --endpoint-url "$R2_ENDPOINT" --query 'ContentLength' --output text)"
REMOTE_SHA256="$(aws s3api head-object --bucket "$BACKUP_BUCKET" --key "$OBJECT_KEY" \
  --endpoint-url "$R2_ENDPOINT" --query 'Metadata.sha256' --output text)"
if [ "$REMOTE_SIZE" != "$BYTES" ] || [ "$REMOTE_SHA256" != "$SHA256" ]; then
  echo "R2 verification failed for s3://${BACKUP_BUCKET}/${OBJECT_KEY}; local backup retained" >&2
  exit 1
fi
aws s3 cp "$MANIFEST" "s3://${BACKUP_BUCKET}/${OBJECT_KEY%.dump.gz}.manifest" \
  --endpoint-url "$R2_ENDPOINT" --metadata "sha256=${SHA256}"
aws s3 cp "${TARGET}.gz.sha256" "s3://${BACKUP_BUCKET}/${OBJECT_KEY}.sha256" \
  --endpoint-url "$R2_ENDPOINT"

# Keep only a small local recovery window. R2 is the canonical 30-day retention store.
mapfile -t LOCAL_BACKUPS < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'skillswap-*.dump.gz' -printf '%T@ %p\n' | sort -nr | awk '{sub($1 FS, ""); print}')
KEEP_COUNT="$LOCAL_BACKUP_KEEP_COUNT"
if [ "$KEEP_COUNT" -lt 2 ]; then KEEP_COUNT=2; fi
for ((i=KEEP_COUNT; i<${#LOCAL_BACKUPS[@]}; i++)); do
  rm -f -- "${LOCAL_BACKUPS[$i]}" "${LOCAL_BACKUPS[$i]}.sha256"
  base="${LOCAL_BACKUPS[$i]%.dump.gz}"
  rm -f -- "${base}.manifest"
done

while [ "$(du -sb "$BACKUP_DIR" | awk '{print $1}')" -gt "$LOCAL_BACKUP_MAX_BYTES" ]; do
  oldest="$(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'skillswap-*.dump.gz' -printf '%T@ %p\n' | sort -n | head -1 | cut -d' ' -f2- || true)"
  [ -n "$oldest" ] || break
  if [ "$oldest" = "${TARGET}.gz" ]; then
    echo "Local backup cap is below the newest backup size; refusing to delete the newest verified copy" >&2
    break
  fi
  rm -f -- "$oldest" "$oldest.sha256" "${oldest%.dump.gz}.manifest"
done

printf '%s\n' "Backup created: ${TARGET}.gz"
printf '%s\n' "BACKUP_FILE=${TARGET}.gz"
printf '%s\n' "BACKUP_MANIFEST=${MANIFEST}"
printf '%s\n' "R2_OBJECT=s3://${BACKUP_BUCKET}/${OBJECT_KEY}"
