#!/bin/bash
# Validate the production contract without printing secret values.

set -Eeuo pipefail

failures=0

deploy_env="${DEPLOY_ENV:-production}"
case "$deploy_env" in
  development|staging|production)
    echo "DEPLOY_ENV=${deploy_env}"
    ;;
  *)
    echo "::error::DEPLOY_ENV must be development, staging, or production" >&2
    failures=1
    ;;
esac

expected_spring_profile="prod"
if [[ "$deploy_env" == "development" ]]; then
  expected_spring_profile="dev"
fi

require_nonempty() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "::error::missing required ${deploy_env} environment variable: ${name}" >&2
    failures=1
  else
    echo "${name}=present"
  fi
}

reject_placeholder() {
  local name="$1"
  local value="${!name:-}"
  case "$value" in
    change-me|replace-with*|test-*|*localhost*|*127.0.0.1*)
      echo "::error::${deploy_env} environment variable contains a development placeholder: ${name}" >&2
      failures=1
      ;;
  esac
}

if [[ "${SPRING_PROFILES_ACTIVE:-}" != "$expected_spring_profile" ]]; then
  echo "::error::SPRING_PROFILES_ACTIVE must be exactly ${expected_spring_profile} for DEPLOY_ENV=${deploy_env}" >&2
  failures=1
fi
if [[ "$deploy_env" != "development" && "${PRODUCTION_CONFIG_VALIDATION_ENABLED:-}" != "true" ]]; then
  echo "::error::PRODUCTION_CONFIG_VALIDATION_ENABLED must be true for DEPLOY_ENV=${deploy_env}" >&2
  failures=1
fi

video_storage_provider="${VIDEO_STORAGE_PROVIDER:-R2}"
video_storage_provider="${video_storage_provider^^}"
case "$video_storage_provider" in
  R2|BUNNY|BUNNY_VIDEO)
    echo "VIDEO_STORAGE_PROVIDER=${video_storage_provider}"
    ;;
  *)
    echo "::error::VIDEO_STORAGE_PROVIDER must be R2 or BUNNY_VIDEO" >&2
    failures=1
    ;;
esac

for name in \
  POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD \
  RABBITMQ_DEFAULT_USER RABBITMQ_DEFAULT_PASS \
  JWT_SECRET_KEY JWT_ISSUER JWT_AUDIENCE CURSOR_AES_KEY CURSOR_HMAC_KEY \
  CORS_ALLOWED_ORIGIN_PATTERNS \
  GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET GOOGLE_CALENDAR_REDIRECT_URI GOOGLE_TOKEN_ENCRYPTION_KEY \
  PAYOS_CLIENT_ID PAYOS_API_KEY PAYOS_CHECKSUM_KEY PAYOS_RETURN_URL PAYOS_CANCEL_URL PAYOS_WEBHOOK_URL PAYOS_WEBHOOK_SECRET \
  SPRING_MAIL_HOST SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD APPLICATION_MAIL_FROM \
  STORAGE_ENDPOINT STORAGE_ACCESS_KEY STORAGE_SECRET_KEY STORAGE_BUCKET; do
  require_nonempty "$name"
done

if [[ "$video_storage_provider" == "BUNNY" || "$video_storage_provider" == "BUNNY_VIDEO" ]]; then
  for name in BUNNY_STREAM_API_KEY BUNNY_STREAM_LIBRARY_ID BUNNY_STREAM_TOKEN_AUTH_KEY BUNNY_STREAM_WEBHOOK_SECRET; do
    require_nonempty "$name"
  done
fi

for name in JWT_SECRET_KEY CURSOR_AES_KEY CURSOR_HMAC_KEY GOOGLE_TOKEN_ENCRYPTION_KEY \
  POSTGRES_PASSWORD RABBITMQ_DEFAULT_PASS PAYOS_API_KEY PAYOS_CHECKSUM_KEY \
  PAYOS_WEBHOOK_SECRET SPRING_MAIL_PASSWORD STORAGE_SECRET_KEY; do
  reject_placeholder "$name"
done

if [[ "$video_storage_provider" == "BUNNY" || "$video_storage_provider" == "BUNNY_VIDEO" ]]; then
  for name in BUNNY_STREAM_API_KEY BUNNY_STREAM_TOKEN_AUTH_KEY BUNNY_STREAM_WEBHOOK_SECRET; do
    reject_placeholder "$name"
  done
fi

if [[ "${APPLICATION_MAIL_ENABLED:-}" != "true" ]]; then
  echo "::error::APPLICATION_MAIL_ENABLED must be true for DEPLOY_ENV=${deploy_env}" >&2
  failures=1
fi
if [[ "${STORAGE_ENABLED:-}" != "true" ]]; then
  echo "::error::STORAGE_ENABLED must be true for DEPLOY_ENV=${deploy_env}" >&2
  failures=1
fi
if [[ "${REALTIME_OUTBOX_ENABLED:-}" != "true" || "${WEBSOCKET_STOMP_ENABLED:-}" != "true" ]]; then
  echo "::error::REALTIME_OUTBOX_ENABLED and WEBSOCKET_STOMP_ENABLED must both be true for DEPLOY_ENV=${deploy_env}" >&2
  failures=1
fi

validate_cors_origins() {
  local raw="${CORS_ALLOWED_ORIGIN_PATTERNS:-}"
  local origin normalized
  local -a origins

  [[ -n "$raw" ]] || return 0

  IFS=',' read -r -a origins <<< "$raw"
  for origin in "${origins[@]}"; do
    origin="${origin#"${origin%%[![:space:]]*}"}"
    origin="${origin%"${origin##*[![:space:]]}"}"
    normalized="${origin,,}"
    case "$normalized" in
      *\**)
        echo "::error::CORS_ALLOWED_ORIGIN_PATTERNS contains a wildcard origin for DEPLOY_ENV=${deploy_env}" >&2
        failures=1
        ;;
      *localhost*|*127.0.0.1*)
        if [[ "$deploy_env" == "production" ]]; then
          echo "::error::CORS_ALLOWED_ORIGIN_PATTERNS contains localhost or 127.0.0.1 for DEPLOY_ENV=production" >&2
          failures=1
        fi
        ;;
      https://?*)
        ;;
      *)
        echo "::error::CORS_ALLOWED_ORIGIN_PATTERNS must contain allowed origins for DEPLOY_ENV=${deploy_env}" >&2
        failures=1
        ;;
    esac
  done
}

validate_cors_origins

for name in PAYOS_RETURN_URL PAYOS_CANCEL_URL PAYOS_WEBHOOK_URL GOOGLE_CALENDAR_REDIRECT_URI; do
  value="${!name:-}"
  if [[ -n "$value" && "$value" != https://* ]]; then
    echo "::error::${name} must use HTTPS for DEPLOY_ENV=${deploy_env}" >&2
    failures=1
  fi
done

if [[ "$failures" -ne 0 ]]; then
  exit 1
fi

echo "Production environment preflight passed."
