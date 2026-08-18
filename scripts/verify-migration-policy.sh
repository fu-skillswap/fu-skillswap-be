#!/bin/sh
set -u

MIN_VERSION="${MIGRATION_POLICY_MIN_VERSION:-69}"
APPROVED_CONTRACT_FILE="${MIGRATION_CONTRACT_APPROVAL_FILE:-scripts/approved-contract-migrations.txt}"
failures=0

is_approved_contract_migration() {
  migration_name="$1"
  [ -f "$APPROVED_CONTRACT_FILE" ] || return 1
  grep -Fqx "$migration_name" "$APPROVED_CONTRACT_FILE"
}

for file in src/main/resources/db/migration/V*.sql; do
  version="$(basename "$file" | sed -n 's/^V\([0-9][0-9]*\)__.*/\1/p')"
  [ -n "$version" ] || continue
  [ "$version" -lt "$MIN_VERSION" ] && continue
  policy="$(sed -n '1,8{s/^[[:space:]]*--[[:space:]]*rollout:[[:space:]]*//p;}' "$file" | tr '[:lower:]' '[:upper:]' | head -1)"
  case "$policy" in
    EXPAND) ;;
    CONTRACT)
      migration_name="$(basename "$file")"
      if is_approved_contract_migration "$migration_name"; then
        echo "Migration $file is an approved CONTRACT release."
      else
        echo "Migration $file is CONTRACT. It requires a dedicated approved contract release." >&2
        failures=1
      fi
      ;;
    *)
      echo "Migration $file must declare '-- rollout: EXPAND' or '-- rollout: CONTRACT' in its first eight lines." >&2
      failures=1
      ;;
  esac
done

if [ "$failures" -ne 0 ]; then
  exit 1
fi

printf '%s\n' "Migration rollout policy passed."
