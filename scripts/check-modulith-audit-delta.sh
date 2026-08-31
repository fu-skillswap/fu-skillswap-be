#!/usr/bin/env bash
set -euo pipefail

report="${1:-target/modulith-audit-edges.csv}"
baseline_file="${2:-docs/architecture/modulith-audit-baseline.properties}"

if [[ ! -f "$report" ]]; then
  echo "Missing Modulith audit report: $report" >&2
  exit 2
fi
if [[ ! -f "$baseline_file" ]]; then
  echo "Missing Modulith audit baseline: $baseline_file" >&2
  exit 2
fi

baseline="$(awk -F= '/^[[:space:]]*distinct_edges[[:space:]]*=/{gsub(/[[:space:]]/, "", $2); print $2; exit}' "$baseline_file")"
if [[ ! "$baseline" =~ ^[0-9]+$ ]]; then
  echo "Invalid distinct_edges baseline in $baseline_file" >&2
  exit 2
fi

actual="$(awk -F, 'NR > 1 && NF >= 3 { print $1 "\034" $2 "\034" $3 }' "$report" | sort -u | wc -l | tr -d '[:space:]')"
delta=$((actual - baseline))
printf 'Modulith distinct non-exposed edges: baseline=%s actual=%s delta=%+d\n' "$baseline" "$actual" "$delta"

if (( delta > 0 )); then
  echo "Audit edge count increased; inspect $report and reject this change." >&2
  exit 1
fi
