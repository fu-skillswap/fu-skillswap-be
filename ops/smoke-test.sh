#!/bin/sh
set -eu

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
fetch_health() {
  url="$1"
  curl --fail --silent --show-error --retry 3 --retry-connrefused --max-time 15 "$url" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'
}

fetch_api() {
  url="$1"
  curl --fail --silent --show-error --retry 3 --retry-connrefused --max-time 15 "$url" | grep -Eq '"status"[[:space:]]*:[[:space:]]*200'
}

fetch_protected() {
  method="$1"
  url="$2"
  status="$(curl --silent --show-error --retry 3 --retry-connrefused --max-time 15 \
    -X "$method" -H 'Content-Type: application/json' --data '{}' \
    -o /dev/null -w '%{http_code}' "$url")"
  case "$status" in
    401|403) ;;
    *)
      printf '%s\n' "Protected endpoint ${method} ${url} returned unexpected HTTP ${status}" >&2
      return 1
      ;;
  esac
}

fetch_health "${BASE_URL}/actuator/health/liveness"
fetch_health "${BASE_URL}/actuator/health/readiness"
fetch_api "${BASE_URL}/api/campuses"
fetch_api "${BASE_URL}/api/mentors"
fetch_protected GET "${BASE_URL}/api/auth/me"
fetch_protected POST "${BASE_URL}/api/bookings"
fetch_protected POST "${BASE_URL}/api/me/payment-orders/checkout"
fetch_protected GET "${BASE_URL}/api/me/conversations"
printf '%s\n' "Public smoke tests passed for ${BASE_URL}"
