#!/usr/bin/env bash
# Deploy one immutable image to the VPS and keep the failure path obvious.

set -Eeuo pipefail

REPOSITORY="${REPOSITORY:?REPOSITORY is required}"
RELEASE_SHA="${RELEASE_SHA:?RELEASE_SHA is required}"
APP_IMAGE="${APP_IMAGE:?APP_IMAGE is required}"
APP_IMAGE="$(printf '%s' "$APP_IMAGE" | tr '[:upper:]' '[:lower:]')"
REGISTRY="${REGISTRY:-ghcr.io}"
REGISTRY_USERNAME="${REGISTRY_USERNAME:?REGISTRY_USERNAME is required}"
REGISTRY_TOKEN="${REGISTRY_TOKEN:?REGISTRY_TOKEN is required}"

# GitHub Actions environment variables can override these values. The defaults
# keep a fresh VPS usable without requiring workflow edits.
DEPLOY_DIR="${DEPLOY_DIR:-/var/opt/fu-skillswap-be}"
requested_release_dir="${RELEASE_DIR:-}"
requested_backup_dir="${BACKUP_DIR:-}"
DEPLOY_LOG_DIR="${DEPLOY_LOG_DIR:-/tmp/skillswap-deploy-${RELEASE_SHA}-$(date -u +%Y%m%dT%H%M%SZ)}"
FAILURE_REPORT="${DEPLOY_LOG_DIR}/failure-report.txt"
RELEASE_PREVIOUS_IMAGE="none"
ROLLBACK_STATUS="not-attempted"
FAILED_COMMAND=""
COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.prod.yml)

diagnose_failure() {
  echo "::group::failure diagnostics"
  echo "failed_command=${FAILED_COMMAND}"
  echo "failed_at=$(date -Is)"
  echo "--- docker ps ---"
  docker ps 2>&1 || true
  echo "--- docker ps -a ---"
  docker ps -a 2>&1 || true
  echo "--- backend inspect ---"
  docker inspect skillswap-backend --format 'status={{.State.Status}} exit={{.State.ExitCode}} error={{.State.Error}} oom={{.State.OOMKilled}} health={{if .State.Health}}{{.State.Health.Status}}{{end}} started={{.State.StartedAt}} finished={{.State.FinishedAt}}' 2>&1 || true
  echo "--- backend logs ---"
  docker logs skillswap-backend --tail 200 2>&1 || true
  echo "--- compose ps ---"
  "${COMPOSE[@]}" ps 2>&1 || true
  echo "--- compose logs ---"
  "${COMPOSE[@]}" logs --tail 200 2>&1 || true
  echo "--- disk ---"
  df -h 2>&1 || true
  echo "--- memory ---"
  free -m 2>&1 || true
  echo "--- docker storage ---"
  docker system df 2>&1 || true
  echo "--- docker service ---"
  systemctl status docker --no-pager 2>&1 || service docker status 2>&1 || true
  echo "::endgroup::"
}

rollback_previous_image() {
  if [[ "$RELEASE_PREVIOUS_IMAGE" == "none" ]]; then
    echo "No previous image was found; automatic rollback is unavailable for the first deployment."
    return 0
  fi

  echo "::group::automatic application rollback"
  echo "candidate_image=${APP_IMAGE}"
  echo "rollback_image=${RELEASE_PREVIOUS_IMAGE}"
  if APP_IMAGE="$RELEASE_PREVIOUS_IMAGE" "${COMPOSE[@]}" up -d --wait spring-backend \
    && curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness; then
    ROLLBACK_STATUS="passed"
    echo "Rollback restored the previous image and readiness is healthy."
  else
    ROLLBACK_STATUS="failed"
    echo "Rollback failed or the previous image did not become ready." >&2
  fi
  echo "::endgroup::"
}

on_deploy_error() {
  local exit_code=$?
  FAILED_COMMAND="${BASH_COMMAND}"
  trap - ERR
  set +e

  mkdir -p "$DEPLOY_LOG_DIR"
  {
    echo "STEP=deployment"
    echo "EXIT_CODE=${exit_code}"
    echo "COMMAND=${FAILED_COMMAND}"
    echo "VERIFY=See the diagnostics printed below and the saved logs in ${DEPLOY_LOG_DIR}."
    echo "FIX=Correct the command/configuration reported by stderr, then rerun the release."
  } >> "$FAILURE_REPORT"

  echo "::error::Deployment failed (exit=${exit_code}): ${FAILED_COMMAND}"
  diagnose_failure
  rollback_previous_image
  echo "Failure report: ${FAILURE_REPORT}"
  cat "$FAILURE_REPORT"
  echo "Rollback status: ${ROLLBACK_STATUS}"
  echo "Diagnostic logs are in ${DEPLOY_LOG_DIR} on VPS."
  exit "$exit_code"
}

trap on_deploy_error ERR

mkdir -p "$DEPLOY_DIR" "$DEPLOY_LOG_DIR"
: > "$FAILURE_REPORT"

echo "::group::deploy context"
echo "repository=${REPOSITORY}"
echo "release_sha=${RELEASE_SHA}"
echo "app_image=${APP_IMAGE}"
echo "deploy_dir=${DEPLOY_DIR}"
echo "deploy_log_dir=${DEPLOY_LOG_DIR}"
date -Is
echo "::endgroup::"

whoami
id
hostname
pwd
date -Is
test -d "$DEPLOY_DIR"
test -w "$DEPLOY_DIR"
cd "$DEPLOY_DIR"

test -r .env
ls -l .env
# Disable nounset while loading the VPS environment because optional values may
# be referenced by an existing env file on a fresh host.
set +u
set -a
source .env
set +a
set -u

# Explicit GitHub Actions variables win; otherwise values in the VPS .env are
# accepted, followed by paths relative to DEPLOY_DIR.
if [[ -n "$requested_release_dir" ]]; then
  RELEASE_DIR="$requested_release_dir"
else
  RELEASE_DIR="${RELEASE_DIR:-${DEPLOY_DIR}/releases}"
fi
if [[ -n "$requested_backup_dir" ]]; then
  BACKUP_DIR="$requested_backup_dir"
else
  BACKUP_DIR="${BACKUP_DIR:-${DEPLOY_DIR}/backups}"
fi
export DEPLOY_DIR RELEASE_DIR BACKUP_DIR

command -v curl
command -v docker
command -v aws
docker --version
docker compose version
aws --version
docker info
docker ps --format 'table {{.Names}}\t{{.Status}}' | head -20

RAW_BASE="https://raw.githubusercontent.com/${REPOSITORY}/${RELEASE_SHA}"
mkdir -p ops/rabbitmq
curl --fail --silent --show-error --location "$RAW_BASE/docker-compose.yml" -o docker-compose.yml
curl --fail --silent --show-error --location "$RAW_BASE/docker-compose.prod.yml" -o docker-compose.prod.yml
curl --fail --silent --show-error --location "$RAW_BASE/ops/rabbitmq/enabled_plugins" -o ops/rabbitmq/enabled_plugins
for script in backup-postgres.sh production-preflight.sh smoke-test.sh write-release-manifest.sh; do
  curl --fail --silent --show-error --location "$RAW_BASE/ops/$script" -o "ops/$script"
  chmod 700 "ops/$script"
done
test -s docker-compose.yml
test -s docker-compose.prod.yml

export RELEASE_CANDIDATE_IMAGE="$APP_IMAGE"
RELEASE_PREVIOUS_IMAGE="$(docker inspect --format '{{.Config.Image}}' skillswap-backend 2>/dev/null || printf '%s' 'none')"

DEPLOY_ENV="${DEPLOY_ENV:-production}" \
  SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}" \
  PRODUCTION_CONFIG_VALIDATION_ENABLED="${PRODUCTION_CONFIG_VALIDATION_ENABLED:-true}" \
  bash ops/production-preflight.sh

if { [[ -n "${BACKUP_BUCKET:-}" ]] && [[ -n "${R2_ENDPOINT:-}" ]] && [[ -n "${AWS_ACCESS_KEY_ID:-}" ]] && [[ -n "${AWS_SECRET_ACCESS_KEY:-}" ]]; } || \
   { [[ -n "${STORAGE_BUCKET:-}" ]] && [[ -n "${STORAGE_ENDPOINT:-}" ]] && [[ -n "${STORAGE_ACCESS_KEY:-}" ]] && [[ -n "${STORAGE_SECRET_KEY:-}" ]]; }; then
  echo "backup R2 configuration=present"
else
  echo "::error::missing backup R2 configuration; configure the backup-specific set or reuse the application storage set" >&2
  exit 1
fi

COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.prod.yml)
"${COMPOSE[@]}" config --quiet

printf '%s' "$REGISTRY_TOKEN" | docker login "$REGISTRY" --username "$REGISTRY_USERNAME" --password-stdin
"${COMPOSE[@]}" pull spring-backend
bash ops/backup-postgres.sh
RELEASE_BACKUP_MANIFEST="$(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'skillswap-*.manifest' -printf '%T@ %p\n' 2>/dev/null | sort -nr | sed -n '1s/^[^ ]* //p')"
export RELEASE_BACKUP_MANIFEST

# This is informational only. The post-deploy query below is the release gate.
if ! docker exec skillswap-postgres psql --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" \
  --tuples-only --command "select installed_rank, version, description, success from flyway_schema_history order by installed_rank;"; then
  echo "Flyway history before deploy is unavailable; continuing because this is a diagnostic." >&2
fi

"${COMPOSE[@]}" up -d --wait postgres-db rabbitmq
docker exec skillswap-rabbitmq rabbitmqctl authenticate_user "$RABBITMQ_DEFAULT_USER" "$RABBITMQ_DEFAULT_PASS"
"${COMPOSE[@]}" up -d --wait spring-backend
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness

failed_migrations="$(docker exec skillswap-postgres psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --tuples-only --no-align --command "select count(*) from flyway_schema_history where success = false;" | tr -d '[:space:]')"
test "$failed_migrations" = 0
bash ops/smoke-test.sh

export RELEASE_MANIFEST_PATH="${RELEASE_DIR}/${RELEASE_SHA}.env"
bash ops/write-release-manifest.sh
echo "Deployment successful for ${APP_IMAGE}"
