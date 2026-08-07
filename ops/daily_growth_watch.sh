#!/bin/bash
# ST-01 & ST-08: Daily DB and Disk Growth Watch
# Retains snapshots for 180 days (6 months).
# Usage: ./ops/daily_growth_watch.sh
# Requires: psql, docker, df, grep, awk

GROWTH_DIR="ops/growth"
mkdir -p "$GROWTH_DIR"

CURRENT_DATE=$(date +"%Y-%m-%d")
CURRENT_MONTH=$(date +"%Y-%m")

SYSTEM_CSV="$GROWTH_DIR/system-$CURRENT_MONTH.csv"
TABLES_CSV="$GROWTH_DIR/tables-$CURRENT_MONTH.csv"

# Initialize CSV headers if files don't exist
if [ ! -f "$SYSTEM_CSV" ]; then
    echo "timestamp,disk_used_bytes,db_bytes,docker_bytes,logs_bytes,temp_bytes" > "$SYSTEM_CSV"
fi

if [ ! -f "$TABLES_CSV" ]; then
    echo "timestamp,table,total_bytes,index_bytes,estimated_rows" > "$TABLES_CSV"
fi

# 1. Collect Disk Used Bytes (Root partition or wherever app resides)
# Note: Adjust the mount point if your VPS uses a different primary partition
DISK_USED_BYTES=$(df -B1 / | awk 'NR==2 {print $3}')

# 2. Collect Docker Bytes (Requires docker running)
if command -v docker >/dev/null 2>&1; then
    DOCKER_BYTES=$(docker system df --format "{{.TotalCount}} {{.Size}}" | awk '{sum+=$1} END {print sum}')
    # Docker size might need parsing since `docker system df` outputs human readable by default.
    # A more robust way to get raw bytes for docker:
    DOCKER_BYTES=$(docker system df --format "{{.Size}}" | grep -o '[0-9\.]*' | awk '{sum+=$1*1024*1024*1024} END {printf "%.0f\n", sum}') # Rough approximation if in GB/MB.
    # Actually, a better approach for raw bytes on linux:
    if [ -d /var/lib/docker ]; then
        DOCKER_BYTES=$(du -sb /var/lib/docker 2>/dev/null | awk '{print $1}')
    fi
else
    DOCKER_BYTES=0
fi

# 3. Collect Logs Bytes (Assuming logs are in /var/log/skillswap or similar, fallback to 0)
LOGS_BYTES=$(du -sb logs/ 2>/dev/null | awk '{print $1}')
if [ -z "$LOGS_BYTES" ]; then LOGS_BYTES=0; fi

# 4. Collect Temp Bytes
TEMP_BYTES=$(du -sb /tmp/skillswap-storage 2>/dev/null | awk '{print $1}')
if [ -z "$TEMP_BYTES" ]; then TEMP_BYTES=0; fi

# 5. Collect DB Bytes via psql (Requires DB_URL or similar ENV vars, assuming local for now)
# Fallback to 0 if psql not available or not configured
DB_BYTES=0
if command -v psql >/dev/null 2>&1; then
    # Assuming skillswap_db is the database name and user postgres is accessible
    DB_BYTES=$(psql -U postgres -d skillswap_db -t -c "SELECT pg_database_size('skillswap_db');" 2>/dev/null | tr -d ' ')
fi
if [ -z "$DB_BYTES" ]; then DB_BYTES=0; fi

# Write System Snapshot
echo "$CURRENT_DATE,$DISK_USED_BYTES,$DB_BYTES,$DOCKER_BYTES,$LOGS_BYTES,$TEMP_BYTES" >> "$SYSTEM_CSV"

# 6. Collect Top Tables Snapshot
if command -v psql >/dev/null 2>&1; then
    psql -U postgres -d skillswap_db -t -c "
        SELECT relname AS table_name,
               pg_total_relation_size(C.oid) AS total_bytes,
               pg_indexes_size(C.oid) AS index_bytes,
               reltuples::bigint AS estimated_rows
        FROM pg_class C
        LEFT JOIN pg_namespace N ON (N.oid = C.relnamespace)
        WHERE nspname NOT IN ('pg_catalog', 'information_schema')
          AND C.relkind <> 'i'
          AND nspname !~ '^pg_toast'
        ORDER BY pg_total_relation_size(C.oid) DESC
        LIMIT 10;
    " | while read -r line; do
        if [ -n "$line" ]; then
            TABLE_NAME=$(echo $line | awk -F'|' '{print $1}' | tr -d ' ')
            TOTAL_BYTES=$(echo $line | awk -F'|' '{print $2}' | tr -d ' ')
            INDEX_BYTES=$(echo $line | awk -F'|' '{print $3}' | tr -d ' ')
            ESTIMATED_ROWS=$(echo $line | awk -F'|' '{print $4}' | tr -d ' ')
            echo "$CURRENT_DATE,$TABLE_NAME,$TOTAL_BYTES,$INDEX_BYTES,$ESTIMATED_ROWS" >> "$TABLES_CSV"
        fi
    done
fi

# 7. Check Disk Thresholds
# DISK_USED_BYTES is in bytes. 1GB = 1073741824 bytes
let DISK_USED_GB=$DISK_USED_BYTES/1073741824

if [ $DISK_USED_GB -gt 60 ]; then
    echo "CRITICAL: Disk usage is $DISK_USED_GB GB (> 60 GB / 75%). Action required."
elif [ $DISK_USED_GB -gt 52 ]; then
    echo "WARNING: Disk usage is $DISK_USED_GB GB (> 52 GB / 65%). Capacity review needed."
elif [ $DISK_USED_GB -gt 40 ]; then
    echo "WATCH: Disk usage is $DISK_USED_GB GB (> 40 GB / 50%). Watch growth."
else
    echo "NORMAL: Disk usage is $DISK_USED_GB GB (< 40 GB)."
fi

# 8. Cleanup old snapshots (older than 180 days)
find "$GROWTH_DIR" -name "*.csv" -type f -mtime +180 -exec rm {} \;

echo "Growth snapshot recorded for $CURRENT_DATE"
