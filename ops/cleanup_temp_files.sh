#!/bin/bash
# ST-07: Temporary File Cleanup Safety Net
# This script is meant to be run via cron (e.g. daily) to clean up orphan temp files
# that were not successfully deleted by the Java application's finally block.
# It only deletes files older than 24 hours to ensure it never deletes files currently in use.

TEMP_DIR="/tmp/skillswap-storage"

# Safety check: ensure directory path is strictly as expected
if [[ "$TEMP_DIR" != "/tmp/skillswap-storage" ]]; then
    echo "ERROR: TEMP_DIR path mismatch. Safety abort."
    exit 1
fi

if [ -d "$TEMP_DIR" ]; then
    echo "Cleaning up temp files in $TEMP_DIR older than 24h..."
    # -mtime +1 means older than 1*24 hours.
    # -type f ensures we only delete files, not directories/symlinks.
    # -delete handles the removal.
    find "$TEMP_DIR" -type f -mtime +1 -print -delete | awk '
        BEGIN { count=0 }
        { count++; print "Deleted orphan temp file: " $0 }
        END { print "Total orphan files deleted: " count }
    '
else
    echo "Directory $TEMP_DIR does not exist. Nothing to clean."
fi
