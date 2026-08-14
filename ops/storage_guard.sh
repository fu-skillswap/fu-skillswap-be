#!/usr/bin/env bash
set -euo pipefail

# Read-only storage guard. It reports an action level but never deletes data.
# A separate, reviewed cleanup job must perform destructive cleanup.
WARN_PERCENT="${STORAGE_WARN_PERCENT:-60}"
ACTION_PERCENT="${STORAGE_ACTION_PERCENT:-70}"
EMERGENCY_PERCENT="${STORAGE_EMERGENCY_PERCENT:-85}"
MIN_FREE_GB="${STORAGE_MIN_FREE_GB:-15}"
BACKUP_DIR="${BACKUP_DIR:-/opt/skillswap/backups}"
POSTGRES_VOLUME="${POSTGRES_VOLUME:-skillswap_postgres_data}"
RABBITMQ_VOLUME="${RABBITMQ_VOLUME:-skillswap_rabbitmq_data}"

bytes_to_human() { numfmt --to=iec --suffix=B "$1" 2>/dev/null || printf '%s bytes' "$1"; }
volume_bytes() {
  local volume="$1" mountpoint
  mountpoint="$(docker volume inspect -f '{{.Mountpoint}}' "$volume" 2>/dev/null || true)"
  if [ -n "$mountpoint" ] && [ -d "$mountpoint" ]; then
    du -sb "$mountpoint" 2>/dev/null | awk '{print $1}'
  else
    printf '0\n'
  fi
}

read -r total used available percent mount < <(df -B1 -P / | awk 'NR==2 {gsub("%", "", $5); print $2, $3, $4, $5, $6}')
free_gb=$((available / 1073741824))
printf 'root total=%s used=%s available=%s usage=%s%% mount=%s\n' \
  "$(bytes_to_human "$total")" "$(bytes_to_human "$used")" "$(bytes_to_human "$available")" "$percent" "$mount"
printf 'postgres_volume=%s bytes (%s)\n' "$(volume_bytes "$POSTGRES_VOLUME")" "$POSTGRES_VOLUME"
printf 'rabbitmq_volume=%s bytes (%s)\n' "$(volume_bytes "$RABBITMQ_VOLUME")" "$RABBITMQ_VOLUME"
printf 'backup_storage=%s bytes (%s)\n' "$(du -sb "$BACKUP_DIR" 2>/dev/null | awk '{print $1}' || printf '0')" "$BACKUP_DIR"
if command -v docker >/dev/null 2>&1; then
  docker system df 2>/dev/null || true
fi

level=NORMAL
if [ "$percent" -ge "$EMERGENCY_PERCENT" ] || [ "$free_gb" -lt "$MIN_FREE_GB" ]; then
  level=EMERGENCY
elif [ "$percent" -ge "$ACTION_PERCENT" ]; then
  level=ARCHIVE_CLEANUP
elif [ "$percent" -ge "$WARN_PERCENT" ]; then
  level=WARN
fi

case "$level" in
  NORMAL) echo "storage_level=NORMAL"; exit 0 ;;
  WARN) echo "storage_level=WARN action=prepare_archive_and_cleanup"; exit 1 ;;
  ARCHIVE_CLEANUP) echo "storage_level=ARCHIVE_CLEANUP action=run_reviewed_retention_jobs"; exit 2 ;;
  EMERGENCY) echo "storage_level=EMERGENCY action=protect_writes_and_page_operator"; exit 3 ;;
esac
