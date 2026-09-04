#!/bin/bash
# Validate the production contract without printing secret values.

set -Eeuo pipefail

failures=0

require_nonempty() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "::error::missing required production environment variable: ${name}" >&2
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
      echo "::error::production environment variable contains a development placeholder: ${name}" >&2
      failures=1
      ;;
  esac
}

if [[ "${SPRING_PROFILES_ACTIVE:-}" != "prod" ]]; then
  echo "::error::SPRING_PROFILES_ACTIVE must be exactly prod for a production deployment" >&2
  failures=1
fi
if [[ "${PRODUCTION_CONFIG_VALIDATION_ENABLED:-}" != "true" ]]; then
  echo "::error::PRODUCTION_CONFIG_VALIDATION_ENABLED must be true in production" >&2
  failures=1
fi

video_storage_provider="${VIDEO_STORAGE_PROVIDER:-R2}"
video_storage_provider="${video_storage_provider^^}"
case "$video_storage_provider" in
  R2|BUNNY)
    echo "VIDEO_STORAGE_PROVIDER=${video_storage_provider}"
    ;;
  *)
    echo "::error::VIDEO_STORAGE_PROVIDER must be R2 or BUNNY" >&2
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

if [[ "$video_storage_provider" == "BUNNY" ]]; then
  for name in BUNNY_STREAM_API_KEY BUNNY_STREAM_LIBRARY_ID BUNNY_STREAM_TOKEN_AUTH_KEY BUNNY_STREAM_WEBHOOK_SECRET; do
    require_nonempty "$name"
  done
fi

for name in JWT_SECRET_KEY CURSOR_AES_KEY CURSOR_HMAC_KEY GOOGLE_TOKEN_ENCRYPTION_KEY \
  POSTGRES_PASSWORD RABBITMQ_DEFAULT_PASS PAYOS_API_KEY PAYOS_CHECKSUM_KEY \
  PAYOS_WEBHOOK_SECRET SPRING_MAIL_PASSWORD STORAGE_SECRET_KEY; do
  reject_placeholder "$name"
done

if [[ "$video_storage_provider" == "BUNNY" ]]; then
  for name in BUNNY_STREAM_API_KEY BUNNY_STREAM_TOKEN_AUTH_KEY BUNNY_STREAM_WEBHOOK_SECRET; do
    reject_placeholder "$name"
  done
fi

if [[ "${APPLICATION_MAIL_ENABLED:-}" != "true" ]]; then
  echo "::error::APPLICATION_MAIL_ENABLED must be true in production" >&2
  failures=1
fi
if [[ "${STORAGE_ENABLED:-}" != "true" ]]; then
  echo "::error::STORAGE_ENABLED must be true in production" >&2
  failures=1
fi
if [[ "${REALTIME_OUTBOX_ENABLED:-}" != "true" || "${WEBSOCKET_STOMP_ENABLED:-}" != "true" ]]; then
  echo "::error::REALTIME_OUTBOX_ENABLED and WEBSOCKET_STOMP_ENABLED must both be true in production" >&2
  failures=1
fi

case "${CORS_ALLOWED_ORIGIN_PATTERNS:-}" in
  *localhost*|*127.0.0.1*|\**)
    echo "::error::CORS_ALLOWED_ORIGIN_PATTERNS contains a local or wildcard origin" >&2
    failures=1
    ;;
esac

for name in PAYOS_RETURN_URL PAYOS_CANCEL_URL PAYOS_WEBHOOK_URL GOOGLE_CALENDAR_REDIRECT_URI; do
  value="${!name:-}"
  if [[ -n "$value" && "$value" != https://* ]]; then
    echo "::error::${name} must use HTTPS in production" >&2
    failures=1
  fi
done

if [[ "$failures" -ne 0 ]]; then
  exit 1
fi

echo "Production environment preflight passed."
