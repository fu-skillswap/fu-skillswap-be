#!/bin/sh
set -eu

MIN_VERSION="${MIGRATION_POLICY_MIN_VERSION:-69}"
for file in src/main/resources/db/migration/V*.sql; do
  version="$(basename "$file" | sed -n 's/^V\([0-9][0-9]*\)__.*/\1/p')"
  [ -n "$version" ] || continue
  [ "$version" -lt "$MIN_VERSION" ] && continue
  policy="$(sed -n '1,8{s/^[[:space:]]*--[[:space:]]*rollout:[[:space:]]*//p;}' "$file" | tr '[:lower:]' '[:upper:]' | head -1)"
  case "$policy" in
    EXPAND) ;;
    CONTRACT)
      echo "Migration $file is CONTRACT. It requires a dedicated approved contract release." >&2
      exit 1
      ;;
    *)
      echo "Migration $file must declare '-- rollout: EXPAND' or '-- rollout: CONTRACT' in its first eight lines." >&2
      exit 1
      ;;
  esac
done
printf '%s\n' "Migration rollout policy passed."
