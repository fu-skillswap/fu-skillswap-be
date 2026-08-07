#!/bin/bash
# ST-11: Database Backup to R2
# Creates a custom-format backup of PostgreSQL and uploads to R2.
# Requires: pg_dump, aws-cli

if ! command -v pg_dump >/dev/null 2>&1; then
    echo "BLOCKED - REQUIRED DEPENDENCY NOT AVAILABLE: pg_dump"
    exit 1
fi

if ! command -v aws >/dev/null 2>&1; then
    echo "BLOCKED - REQUIRED DEPENDENCY NOT AVAILABLE: aws-cli"
    exit 1
fi

# Environment checks
if [ -z "$BACKUP_BUCKET" ] || [ -z "$R2_ENDPOINT" ] || [ -z "$AWS_ACCESS_KEY_ID" ] || [ -z "$AWS_SECRET_ACCESS_KEY" ]; then
    echo "BLOCKED - REQUIRED ENV VARS MISSING: BACKUP_BUCKET, R2_ENDPOINT, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY"
    exit 1
fi

DB_USER=${DB_USER:-"postgres"}
DB_NAME=${DB_NAME:-"skillswap_db"}
DATE=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="backup_${DB_NAME}_${DATE}.dump"

echo "Starting backup of ${DB_NAME}..."

# Custom format (-Fc) is compressed by default and suitable for pg_restore
pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc -f "$BACKUP_FILE"

if [ $? -eq 0 ]; then
    echo "Backup created successfully: $BACKUP_FILE"
    echo "Uploading to R2..."
    
    # Upload to R2 using AWS CLI with custom endpoint
    aws s3 cp "$BACKUP_FILE" "s3://${BACKUP_BUCKET}/db_backups/${BACKUP_FILE}" --endpoint-url "$R2_ENDPOINT"
    
    if [ $? -eq 0 ]; then
        echo "Upload successful."
        # Clean up local file after successful upload
        rm "$BACKUP_FILE"
        echo "Local backup file deleted."
    else
        echo "Upload failed! Retaining local file: $BACKUP_FILE"
        exit 1
    fi
else
    echo "pg_dump failed!"
    exit 1
fi
