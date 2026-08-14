#!/usr/bin/env bash
set -euo pipefail

# Compatibility entrypoint. Keep one backup implementation and one retention policy.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "$SCRIPT_DIR/backup-postgres.sh" "$@"
