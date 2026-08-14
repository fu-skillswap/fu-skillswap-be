#!/bin/sh
set -u

failures=0

fail() {
  printf '%s\n' "::error::$1" >&2
  failures=1
}

if [ "${REQUIRE_CLEAN_WORKTREE:-true}" = "true" ] && [ -n "$(git status --porcelain)" ]; then
  fail "Release gate failed: worktree is not clean."
fi

if ! git diff --check; then
  fail "Release gate failed: git diff contains whitespace errors."
fi

for script in ops/*.sh scripts/verify-migration-policy.sh; do
  interpreter=sh
  case "$(sed -n '1p' "$script")" in
    '#!/bin/bash'|'#!/usr/bin/env bash') interpreter=bash ;;
  esac

  if ! "$interpreter" -n "$script"; then
    fail "Release gate failed: shell syntax is invalid in $script."
  fi
done

# .env.example deliberately leaves production cursor keys blank. Supply placeholders
# only for interpolation; this command never starts a container or validates secrets.
if ! APP_IMAGE=skillswap/preflight:local \
  CURSOR_AES_KEY=preflight-placeholder \
  CURSOR_HMAC_KEY=preflight-placeholder \
  docker compose --env-file .env.example -f docker-compose.yml -f docker-compose.prod.yml config --quiet; then
  fail "Release gate failed: production Compose topology is invalid."
fi

if [ "$failures" -ne 0 ]; then
  exit 1
fi

printf '%s\n' "Release preflight passed."
