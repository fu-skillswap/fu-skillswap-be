#!/bin/bash
# ST-11: Database Backup Restore Drill
# Downloads the specified backup from R2 and restores it to a TEST database.
# Performs sanity checks on the restored database.
# Requires: pg_restore, aws-cli, psql

if ! command -v pg_restore >/dev/null 2>&1; then
    echo "BLOCKED - REQUIRED DEPENDENCY NOT AVAILABLE: pg_restore"
    exit 1
fi

if ! command -v aws >/dev/null 2>&1; then
    echo "BLOCKED - REQUIRED DEPENDENCY NOT AVAILABLE: aws-cli"
    exit 1
fi

if ! command -v psql >/dev/null 2>&1; then
    echo "BLOCKED - REQUIRED DEPENDENCY NOT AVAILABLE: psql"
    exit 1
fi

if [ -z "$BACKUP_BUCKET" ] || [ -z "$R2_ENDPOINT" ] || [ -z "$AWS_ACCESS_KEY_ID" ] || [ -z "$AWS_SECRET_ACCESS_KEY" ]; then
    echo "BLOCKED - REQUIRED ENV VARS MISSING"
    exit 1
fi

if [ -z "$1" ]; then
    echo "Usage: $0 <backup-file-name.dump.gz>"
    exit 1
fi

BACKUP_FILE=$1
TEST_DB="skillswap_test_restore"
DB_USER=${DB_USER:-"postgres"}

echo "Downloading $BACKUP_FILE from R2..."
aws s3 cp "s3://${BACKUP_BUCKET}/db_backups/${BACKUP_FILE}" "./${BACKUP_FILE}" --endpoint-url "$R2_ENDPOINT"
aws s3 cp "s3://${BACKUP_BUCKET}/db_backups/${BACKUP_FILE}.sha256" "./${BACKUP_FILE}.sha256" --endpoint-url "$R2_ENDPOINT"

if [ $? -ne 0 ]; then
    echo "Failed to download backup."
    exit 1
fi

aws s3api head-object --bucket "$BACKUP_BUCKET" --key "db_backups/${BACKUP_FILE}" \
    --endpoint-url "$R2_ENDPOINT" --query 'ContentLength' --output text >/tmp/skillswap-restore-remote-size
REMOTE_SIZE=$(cat /tmp/skillswap-restore-remote-size)
LOCAL_SIZE=$(wc -c < "./${BACKUP_FILE}" | tr -d ' ')
if [ "$REMOTE_SIZE" != "$LOCAL_SIZE" ]; then
    echo "Downloaded backup size does not match the R2 object" >&2
    rm -f "./${BACKUP_FILE}" /tmp/skillswap-restore-remote-size
    exit 1
fi
rm -f /tmp/skillswap-restore-remote-size

if [ -f "./${BACKUP_FILE}.sha256" ]; then
    sha256sum -c "./${BACKUP_FILE}.sha256"
fi

echo "Dropping and recreating test database: $TEST_DB"
psql -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS $TEST_DB;"
psql -U "$DB_USER" -d postgres -c "CREATE DATABASE $TEST_DB;"

echo "Restoring to $TEST_DB..."
pg_restore -U "$DB_USER" -d "$TEST_DB" -1 "./${BACKUP_FILE}"

if [ $? -eq 0 ]; then
    echo "Restore completed successfully. Performing sanity checks..."
    
    # Check if a critical table exists and has rows
    USERS_COUNT=$(psql -U "$DB_USER" -d "$TEST_DB" -t -c "SELECT count(*) FROM users;" 2>/dev/null | tr -d ' ')
    if [ -n "$USERS_COUNT" ]; then
        echo "Sanity Check PASS: 'users' table exists and has $USERS_COUNT rows."
    else
        echo "Sanity Check FAIL: 'users' table not found or query failed."
    fi

    # Check flyway metadata
    MIGRATIONS=$(psql -U "$DB_USER" -d "$TEST_DB" -t -c "SELECT count(*) FROM flyway_schema_history;" 2>/dev/null | tr -d ' ')
    if [ -n "$MIGRATIONS" ]; then
        echo "Sanity Check PASS: Flyway schema history intact with $MIGRATIONS migrations."
    else
        echo "Sanity Check FAIL: Flyway metadata missing."
    fi

    echo "Restore drill completed!"
else
    echo "Restore failed!"
fi

echo "Cleaning up local downloaded backup..."
rm -f "./${BACKUP_FILE}" "./${BACKUP_FILE}.sha256"
