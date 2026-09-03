#!/bin/bash
# ST-11: Database Backup Restore Drill
# Downloads a verified backup from R2 and restores it to an isolated test database.
# This script is intentionally fail-closed: a restore or verification error must
# produce a non-zero exit status.
# Requires: pg_restore, aws-cli, psql

set -Eeuo pipefail

# Safety checks run before dependency checks so an unsafe target is rejected
# even on a workstation that does not have PostgreSQL tooling installed.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/restore-drill-guards.sh"
validate_restore_target

for dependency in pg_restore aws psql sha256sum gzip; do
    if ! command -v "$dependency" >/dev/null 2>&1; then
        echo "BLOCKED - REQUIRED DEPENDENCY NOT AVAILABLE: $dependency" >&2
        exit 1
    fi
done

BACKUP_BUCKET="${BACKUP_BUCKET:-${STORAGE_BUCKET:-}}"
R2_ENDPOINT="${R2_ENDPOINT:-${STORAGE_ENDPOINT:-}}"
AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-${STORAGE_ACCESS_KEY:-}}"
AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-${STORAGE_SECRET_KEY:-}}"
AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-${STORAGE_REGION:-auto}}"
export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_DEFAULT_REGION

if [[ -z "$BACKUP_BUCKET" || -z "$R2_ENDPOINT" || -z "$AWS_ACCESS_KEY_ID" || -z "$AWS_SECRET_ACCESS_KEY" ]]; then
    echo "BLOCKED - REQUIRED R2 ENV VARS MISSING" >&2
    exit 1
fi

if [[ -z "${1:-}" ]]; then
    echo "Usage: $0 <backup-file-name.dump.gz>" >&2
    exit 1
fi

BACKUP_FILE="$1"
if [[ "$BACKUP_FILE" == */* || "$BACKUP_FILE" != *.dump.gz ]]; then
    echo "Backup file must be a simple .dump.gz filename, not a path." >&2
    exit 1
fi

TEST_DB="${TEST_DB}"

DB_USER="${DB_USER:-postgres}"
EXPECTED_FLYWAY_VERSION="${EXPECTED_FLYWAY_VERSION:-130}"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/skillswap-restore.XXXXXX")"
RESTORE_PATH="${WORK_DIR}/${BACKUP_FILE}"
CHECKSUM_PATH="${RESTORE_PATH}.sha256"

cleanup() {
    rm -rf -- "$WORK_DIR"
}
trap cleanup EXIT

psql_test() {
    psql --set=ON_ERROR_STOP=1 --tuples-only --no-align -U "$DB_USER" -d "$1" -c "$2"
}

assert_zero() {
    local description="$1"
    local query="$2"
    local value
    value="$(psql_test "$TEST_DB" "$query" | tr -d '[:space:]')"
    if [[ "$value" != "0" ]]; then
        echo "Restore verification failed: ${description} (value=${value})" >&2
        exit 1
    fi
    echo "PASS: ${description}"
}

echo "Downloading ${BACKUP_FILE} from R2..."
aws s3 cp "s3://${BACKUP_BUCKET}/db_backups/${BACKUP_FILE}" "$RESTORE_PATH" --endpoint-url "$R2_ENDPOINT"
aws s3 cp "s3://${BACKUP_BUCKET}/db_backups/${BACKUP_FILE}.sha256" "$CHECKSUM_PATH" --endpoint-url "$R2_ENDPOINT"
test -s "$RESTORE_PATH"
test -s "$CHECKSUM_PATH"

REMOTE_SIZE="$(aws s3api head-object --bucket "$BACKUP_BUCKET" --key "db_backups/${BACKUP_FILE}" \
    --endpoint-url "$R2_ENDPOINT" --query 'ContentLength' --output text)"
LOCAL_SIZE="$(wc -c < "$RESTORE_PATH" | tr -d '[:space:]')"
if [[ "$REMOTE_SIZE" != "$LOCAL_SIZE" ]]; then
    echo "Downloaded backup size does not match the R2 object." >&2
    exit 1
fi
sha256sum -c "$CHECKSUM_PATH"

echo "Dropping and recreating isolated test database: ${TEST_DB}"
psql --set=ON_ERROR_STOP=1 -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS ${TEST_DB};"
psql --set=ON_ERROR_STOP=1 -U "$DB_USER" -d postgres -c "CREATE DATABASE ${TEST_DB};"

echo "Restoring to ${TEST_DB}..."
gzip -dc "$RESTORE_PATH" > "${WORK_DIR}/restore.dump"
pg_restore --list "${WORK_DIR}/restore.dump" >/dev/null
pg_restore --exit-on-error --single-transaction -U "$DB_USER" -d "$TEST_DB" "${WORK_DIR}/restore.dump"
echo "PASS - restore execution"

echo "Verifying database connectivity and required tables..."
psql_test "$TEST_DB" "SELECT 1;" | grep -qx '1'
for table in users bookings payment_orders conversations messages courses; do
    exists="$(psql_test "$TEST_DB" "SELECT CASE WHEN to_regclass('public.${table}') IS NULL THEN '0' ELSE '1' END;" | tr -d '[:space:]')"
    if [[ "$exists" != "1" ]]; then
        echo "Restore verification failed: required table ${table} is missing." >&2
        exit 1
    fi
    echo "PASS: required table ${table} exists"
done
echo "SCHEMA RESTORE PASS"

latest_migration="$(psql_test "$TEST_DB" "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1;" | tr -d '[:space:]')"
if [[ "$latest_migration" != "$EXPECTED_FLYWAY_VERSION" ]]; then
    echo "Restore verification failed: expected Flyway version ${EXPECTED_FLYWAY_VERSION}, got ${latest_migration:-none}." >&2
    exit 1
fi
echo "PASS: Flyway version ${latest_migration}"

users_count="$(psql_test "$TEST_DB" "SELECT count(*) FROM users;" | tr -d '[:space:]')"
if [[ "${users_count:-0}" -lt 1 ]]; then
    echo "Restore verification failed: users table is empty." >&2
    exit 1
fi
echo "PASS: users table contains ${users_count} row(s)"

for table in bookings payment_orders conversations messages courses; do
    count="$(psql_test "$TEST_DB" "SELECT count(*) FROM ${table};" | tr -d '[:space:]')"
    echo "INFO: ${table} row count=${count}"
done

assert_zero "unvalidated foreign keys" \
    "SELECT count(*) FROM pg_constraint WHERE contype = 'f' AND NOT convalidated;"
assert_zero "bookings with NULL status" \
    "SELECT count(*) FROM bookings WHERE status IS NULL;"
assert_zero "bookings with unknown status" \
    "SELECT count(*) FROM bookings WHERE status NOT IN (${BOOKING_STATUS_SQL});"
assert_zero "payment orders with NULL status" \
    "SELECT count(*) FROM payment_orders WHERE status IS NULL;"
assert_zero "payment orders with unknown status" \
    "SELECT count(*) FROM payment_orders WHERE status NOT IN (${PAYMENT_ORDER_STATUS_SQL});"

assert_zero "bookings with invalid mentee references" \
    "SELECT count(*) FROM bookings b WHERE b.mentee_user_id IS NULL OR NOT EXISTS (SELECT 1 FROM users u WHERE u.id = b.mentee_user_id);"
assert_zero "bookings with invalid mentor references" \
    "SELECT count(*) FROM bookings b WHERE b.mentor_user_id IS NULL OR NOT EXISTS (SELECT 1 FROM mentor_profiles m WHERE m.user_id = b.mentor_user_id);"
assert_zero "bookings with invalid service references" \
    "SELECT count(*) FROM bookings b WHERE b.service_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM mentor_services s WHERE s.id = b.service_id);"
assert_zero "bookings with invalid slot references" \
    "SELECT count(*) FROM bookings b WHERE b.slot_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM mentor_availability_slots s WHERE s.id = b.slot_id);"
assert_zero "payment orders with invalid booking references" \
    "SELECT count(*) FROM payment_orders p WHERE p.booking_id IS NULL OR NOT EXISTS (SELECT 1 FROM bookings b WHERE b.id = p.booking_id);"
assert_zero "payment orders with invalid payer references" \
    "SELECT count(*) FROM payment_orders p WHERE p.payer_user_id IS NULL OR NOT EXISTS (SELECT 1 FROM users u WHERE u.id = p.payer_user_id);"
assert_zero "payment orders with invalid mentor references" \
    "SELECT count(*) FROM payment_orders p WHERE p.mentor_user_id IS NULL OR NOT EXISTS (SELECT 1 FROM mentor_profiles m WHERE m.user_id = p.mentor_user_id);"
assert_zero "payment orders with invalid service references" \
    "SELECT count(*) FROM payment_orders p WHERE p.service_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM mentor_services s WHERE s.id = p.service_id);"
assert_zero "payment attempts with invalid payment-order references" \
    "SELECT count(*) FROM payment_attempts p WHERE p.payment_order_id IS NULL OR NOT EXISTS (SELECT 1 FROM payment_orders o WHERE o.id = p.payment_order_id);"
echo "BUSINESS STATE VALIDATION PASS"

# Existing backup manifests prove object identity and database identity, but do
# not contain source row counts. Accept an optional table=count file when one
# is supplied; otherwise report completeness as UNVERIFIED instead of claiming
# that every critical table was recovered.
SOURCE_ROW_COUNTS_FILE="${SOURCE_ROW_COUNTS_FILE:-}"
if [[ -n "$SOURCE_ROW_COUNTS_FILE" && -f "$SOURCE_ROW_COUNTS_FILE" ]]; then
    for table in users bookings payment_orders conversations messages courses; do
        expected="$(awk -F= -v table="$table" '$1 == table {print $2; exit}' "$SOURCE_ROW_COUNTS_FILE")"
        if [[ ! "$expected" =~ ^[0-9]+$ ]]; then
            echo "UNVERIFIED - missing source row count for ${table}." >&2
            continue
        fi
        actual="$(psql_test "$TEST_DB" "SELECT count(*) FROM ${table};" | tr -d '[:space:]')"
        if [[ "$actual" != "$expected" ]]; then
            echo "FAIL - ${table} row count mismatch: expected ${expected}, got ${actual}." >&2
            exit 1
        fi
        echo "PASS - ${table} row count matches source evidence (${actual})"
    done
    echo "BUSINESS DATA RECOVERY VERIFIED"
else
    echo "UNVERIFIED - no source row-count evidence supplied; critical-table completeness was not proven." >&2
fi

echo "Restore drill status: SCHEMA RESTORE PASS; business completeness is reported separately above."
