#!/bin/sh
set -eu

: "${CANDIDATE_IMAGE:?CANDIDATE_IMAGE is required}"
: "${REHEARSAL_BACKUP_FILE:?REHEARSAL_BACKUP_FILE is required}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"
: "${RABBITMQ_DEFAULT_USER:?RABBITMQ_DEFAULT_USER is required}"
: "${RABBITMQ_DEFAULT_PASS:?RABBITMQ_DEFAULT_PASS is required}"
: "${JWT_SECRET_KEY:?JWT_SECRET_KEY is required}"
: "${JWT_ISSUER:?JWT_ISSUER is required}"
: "${JWT_AUDIENCE:?JWT_AUDIENCE is required}"
: "${CURSOR_AES_KEY:?CURSOR_AES_KEY is required}"
: "${CURSOR_HMAC_KEY:?CURSOR_HMAC_KEY is required}"

test -f "$REHEARSAL_BACKUP_FILE"
RUN_ID="${REHEARSAL_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
PREFIX="skillswap-rehearsal-${RUN_ID}"
NETWORK="${PREFIX}-network"
DB="${PREFIX}-postgres"
RABBIT="${PREFIX}-rabbitmq"
BACKEND="${PREFIX}-backend"
VOLUME="${PREFIX}-postgres-data"
PORT="${REHEARSAL_PORT:-18080}"
EVIDENCE_DIR="${REHEARSAL_EVIDENCE_DIR:-/opt/skillswap-staging/release-evidence}/${RUN_ID}"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
STARTED_SECONDS="$(date +%s)"

mkdir -p "$EVIDENCE_DIR"
umask 077

cleanup() {
  docker rm -f "$BACKEND" "$RABBIT" "$DB" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
  docker volume rm "$VOLUME" >/dev/null 2>&1 || true
  if [ "${REHEARSAL_DELETE_INPUT_BACKUP:-false}" = "true" ]; then
    rm -f "$REHEARSAL_BACKUP_FILE" "${REHEARSAL_BACKUP_FILE}.sha256"
  fi
}
trap cleanup EXIT HUP INT TERM

capture_counts() {
  docker exec "$DB" psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --tuples-only --no-align --command "
    select 'users=' || count(*) from users;
    select 'bookings=' || count(*) from bookings;
    select 'payment_orders=' || count(*) from payment_orders;
    select 'credit_ledger_entries=' || count(*) from credit_ledger_entries;
    select 'domain_event_outbox=' || count(*) from domain_event_outbox;
  "
}

docker network create "$NETWORK" >/dev/null
docker volume create "$VOLUME" >/dev/null
docker run -d --name "$DB" --network "$NETWORK" \
  -e POSTGRES_DB -e POSTGRES_USER -e POSTGRES_PASSWORD \
  -v "${VOLUME}:/var/lib/postgresql/data" postgres:17-alpine >/dev/null

until docker exec "$DB" pg_isready --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" >/dev/null 2>&1; do sleep 2; done

chmod 600 "$REHEARSAL_BACKUP_FILE"
if [ -f "${REHEARSAL_BACKUP_FILE}.sha256" ]; then
  (cd "$(dirname "$REHEARSAL_BACKUP_FILE")" && sha256sum -c "$(basename "${REHEARSAL_BACKUP_FILE}").sha256")
fi
TEMP_DUMP="$(mktemp)"
trap 'rm -f "$TEMP_DUMP"; cleanup' EXIT HUP INT TERM
gzip -dc "$REHEARSAL_BACKUP_FILE" > "$TEMP_DUMP"
docker cp "$TEMP_DUMP" "${DB}:/tmp/rehearsal.dump"
docker exec "$DB" pg_restore --list /tmp/rehearsal.dump >/dev/null
RESTORE_STARTED_SECONDS="$(date +%s)"
docker exec "$DB" pg_restore --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --no-owner --exit-on-error /tmp/rehearsal.dump
RESTORE_FINISHED_SECONDS="$(date +%s)"
docker exec "$DB" rm -f /tmp/rehearsal.dump
rm -f "$TEMP_DUMP"

docker exec "$DB" psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --tuples-only --no-align --command "select installed_rank || '|' || version || '|' || description || '|' || success from flyway_schema_history order by installed_rank" > "${EVIDENCE_DIR}/flyway-before.txt"
capture_counts > "${EVIDENCE_DIR}/counts-before.txt"

docker run -d --name "$RABBIT" --network "$NETWORK" \
  -e RABBITMQ_DEFAULT_USER -e RABBITMQ_DEFAULT_PASS rabbitmq:3.13-management-alpine >/dev/null
until docker exec "$RABBIT" rabbitmq-diagnostics -q ping >/dev/null 2>&1; do sleep 2; done

docker run -d --name "$BACKEND" --network "$NETWORK" -p "127.0.0.1:${PORT}:8080" \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e PRODUCTION_CONFIG_VALIDATION_ENABLED=true \
  -e DATABASE_URL="jdbc:postgresql://${DB}:5432/${POSTGRES_DB}" \
  -e DATABASE_USERNAME="$POSTGRES_USER" -e DATABASE_PASSWORD="$POSTGRES_PASSWORD" \
  -e FLYWAY_ENABLED=true -e HIBERNATE_DDL_AUTO=validate \
  -e JWT_SECRET_KEY -e JWT_ISSUER -e JWT_AUDIENCE -e CURSOR_AES_KEY -e CURSOR_HMAC_KEY \
  -e SPRING_RABBITMQ_HOST="$RABBIT" -e SPRING_RABBITMQ_PORT=5672 \
  -e SPRING_RABBITMQ_USERNAME="$RABBITMQ_DEFAULT_USER" -e SPRING_RABBITMQ_PASSWORD="$RABBITMQ_DEFAULT_PASS" \
  -e APPLICATION_MAIL_ENABLED=true -e APPLICATION_MAIL_FROM=rehearsal@invalid -e STORAGE_ENABLED=true -e APPLICATION_SWAGGER_ENABLED=false \
  -e APPLICATION_SCHEDULING_ENABLED=false \
  -e SPRING_MAIL_HOST=localhost -e SPRING_MAIL_USERNAME=rehearsal -e SPRING_MAIL_PASSWORD=rehearsal \
  -e GOOGLE_CLIENT_ID=rehearsal-google-client -e GOOGLE_CLIENT_SECRET=rehearsal-google-secret -e GOOGLE_CALENDAR_REDIRECT_URI=https://rehearsal.invalid/callback \
  -e GOOGLE_TOKEN_ENCRYPTION_KEY=rehearsal-google-token-key -e SYSTEM_ADMIN_EMAILS= \
  -e BUNNY_STREAM_API_KEY=rehearsal-bunny-api-key -e BUNNY_STREAM_LIBRARY_ID=rehearsal-library \
  -e BUNNY_STREAM_TOKEN_AUTH_KEY=rehearsal-bunny-token-key -e BUNNY_STREAM_WEBHOOK_SECRET=rehearsal-bunny-webhook-secret \
  -e STORAGE_ENDPOINT=https://rehearsal.invalid/storage -e STORAGE_ACCESS_KEY=rehearsal-storage-access \
  -e STORAGE_SECRET_KEY=rehearsal-storage-secret -e STORAGE_BUCKET=rehearsal-bucket \
  -e CORS_ALLOWED_ORIGIN_PATTERNS="https://rehearsal.invalid" \
  -e PAYOS_CLIENT_ID=rehearsal-payos-client -e PAYOS_API_KEY=rehearsal-payos-api-key -e PAYOS_CHECKSUM_KEY=rehearsal-payos-checksum \
  -e PAYOS_WEBHOOK_URL=https://rehearsal.invalid/payos-webhook -e PAYOS_WEBHOOK_SECRET=rehearsal-payos-webhook-secret \
  -e PAYOS_RETURN_URL=https://rehearsal.invalid/payment/return -e PAYOS_CANCEL_URL=https://rehearsal.invalid/payment/cancel \
  "$CANDIDATE_IMAGE" >/dev/null

CANDIDATE_STARTED_SECONDS="$(date +%s)"
READY_DEADLINE=$(( $(date +%s) + ${REHEARSAL_READINESS_TIMEOUT_SECONDS:-180} ))
until curl --fail --silent --show-error "http://127.0.0.1:${PORT}/actuator/health/readiness" >/dev/null 2>&1; do
  if [ "$(date +%s)" -ge "$READY_DEADLINE" ]; then
    docker logs "$BACKEND" > "${EVIDENCE_DIR}/backend-failure.log" 2>&1 || true
    echo "Candidate backend did not become ready." >&2
    exit 1
  fi
  sleep 3
done
CANDIDATE_READY_SECONDS="$(date +%s)"

BASE_URL="http://127.0.0.1:${PORT}" "$(dirname "$0")/smoke-test.sh" > "${EVIDENCE_DIR}/smoke.txt"
docker exec "$DB" psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --tuples-only --no-align --command "select installed_rank || '|' || version || '|' || description || '|' || success from flyway_schema_history order by installed_rank" > "${EVIDENCE_DIR}/flyway-after.txt"
capture_counts > "${EVIDENCE_DIR}/counts-after.txt"
FINISHED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
FINISHED_SECONDS="$(date +%s)"
cat > "${EVIDENCE_DIR}/manifest.env" <<EOF
run_id=${RUN_ID}
candidate_image=${CANDIDATE_IMAGE}
backup_file=${REHEARSAL_BACKUP_FILE}
backup_sha256=$(sha256sum "$REHEARSAL_BACKUP_FILE" | awk '{print $1}')
started_at_utc=${STARTED_AT}
finished_at_utc=${FINISHED_AT}
restore_duration_seconds=$((RESTORE_FINISHED_SECONDS - RESTORE_STARTED_SECONDS))
candidate_flyway_and_startup_duration_seconds=$((CANDIDATE_READY_SECONDS - CANDIDATE_STARTED_SECONDS))
total_duration_seconds=$((FINISHED_SECONDS - STARTED_SECONDS))
smoke=passed
EOF
printf '%s\n' "Migration rehearsal passed. Evidence: ${EVIDENCE_DIR}"
