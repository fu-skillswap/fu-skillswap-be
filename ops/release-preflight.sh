#!/bin/sh
set -eu

if [ "${REQUIRE_CLEAN_WORKTREE:-true}" = "true" ] && [ -n "$(git status --porcelain)" ]; then
  echo "Release gate failed: worktree is not clean." >&2
  exit 1
fi

git diff --check
for script in ops/*.sh; do
  sh -n "$script"
done
APP_IMAGE=skillswap/preflight:local docker compose --env-file .env.example -f docker-compose.yml -f docker-compose.prod.yml config --quiet
printf '%s\n' "Release preflight passed."
