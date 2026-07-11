#!/bin/sh
set -eu

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
curl --fail --silent --show-error "${BASE_URL}/actuator/health/liveness" >/dev/null
curl --fail --silent --show-error "${BASE_URL}/actuator/health/readiness" >/dev/null
curl --fail --silent --show-error "${BASE_URL}/api/campuses" >/dev/null
printf '%s\n' "Public smoke tests passed for ${BASE_URL}"
