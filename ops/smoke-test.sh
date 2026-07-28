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

fetch_health "${BASE_URL}/actuator/health/liveness"
fetch_health "${BASE_URL}/actuator/health/readiness"
fetch_api "${BASE_URL}/api/campuses"
fetch_api "${BASE_URL}/api/mentors"
printf '%s\n' "Public smoke tests passed for ${BASE_URL}"
