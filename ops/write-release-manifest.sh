#!/bin/sh
set -eu

: "${RELEASE_MANIFEST_PATH:?RELEASE_MANIFEST_PATH is required}"
: "${RELEASE_CANDIDATE_IMAGE:?RELEASE_CANDIDATE_IMAGE is required}"

mkdir -p "$(dirname "$RELEASE_MANIFEST_PATH")"
umask 077
FLYWAY_HISTORY=""
if docker ps --format '{{.Names}}' | grep -qx 'skillswap-postgres'; then
  FLYWAY_HISTORY="$(docker exec skillswap-postgres psql --username "${POSTGRES_USER:?POSTGRES_USER is required}" --dbname "${POSTGRES_DB:?POSTGRES_DB is required}" --tuples-only --no-align --command "select installed_rank || '|' || version || '|' || description || '|' || success from flyway_schema_history order by installed_rank" | tr '\n' ';')"
fi

BACKUP_SHA256=""
if [ -n "${RELEASE_BACKUP_MANIFEST:-}" ] && [ -r "$RELEASE_BACKUP_MANIFEST" ]; then
  BACKUP_SHA256="$(sed -n 's/^sha256=//p' "$RELEASE_BACKUP_MANIFEST" | head -1)"
fi

cat > "$RELEASE_MANIFEST_PATH" <<EOF
released_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
candidate_image=${RELEASE_CANDIDATE_IMAGE}
previous_image=${RELEASE_PREVIOUS_IMAGE:-unknown}
backup_manifest=${RELEASE_BACKUP_MANIFEST:-none}
backup_sha256=${BACKUP_SHA256:-none}
smoke=passed
flyway_history=${FLYWAY_HISTORY:-unavailable}
EOF
chmod 600 "$RELEASE_MANIFEST_PATH"
printf '%s\n' "Release manifest written: ${RELEASE_MANIFEST_PATH}"
