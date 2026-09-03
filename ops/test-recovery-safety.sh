#!/bin/bash
# Credential-free regression checks for recovery-drill safety rules.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/restore-drill-guards.sh"

pass() { echo "PASS - $1"; }
fail() { echo "FAIL - $1" >&2; exit 1; }

if (
    unset TEST_DB
    POSTGRES_DB=skillswapdb
    RESTORE_TARGET_ISOLATED=true
    validate_restore_target 2>/dev/null
); then fail "missing TEST_DB was accepted"; else pass "missing TEST_DB is rejected"; fi

if (
    TEST_DB=skillswap_restore_test_case
    POSTGRES_DB=skillswap_restore_test_case
    RESTORE_TARGET_ISOLATED=true
    validate_restore_target 2>/dev/null
); then fail "TEST_DB equal to production database was accepted"; else pass "TEST_DB equal to production database is rejected"; fi

if (
    TEST_DB=skillswapdb
    POSTGRES_DB=skillswap_production
    RESTORE_TARGET_ISOLATED=true
    validate_restore_target 2>/dev/null
); then fail "unsafe test database name was accepted"; else pass "unsafe test database name is rejected"; fi

if (
    TEST_DB=skillswap_restore_test_case
    POSTGRES_DB=skillswapdb
    RESTORE_TARGET_ISOLATED=true
    RESTORE_TARGET_HOST=skillswap-prod-db
    validate_restore_target 2>/dev/null
); then fail "production-like target host was accepted"; else pass "production-like target host is rejected"; fi

if ! (
    TEST_DB=skillswap_restore_test_case
    POSTGRES_DB=skillswapdb
    RESTORE_TARGET_ISOLATED=true
    RESTORE_TARGET_HOST=localhost
    validate_restore_target
); then fail "safe isolated restore target was rejected"; else pass "safe isolated restore target is accepted"; fi

if is_valid_booking_status INVALID_STATUS; then fail "invalid booking status was accepted"; else pass "invalid booking status is rejected"; fi
if is_valid_payment_order_status INVALID_STATUS; then fail "invalid payment status was accepted"; else pass "invalid payment status is rejected"; fi
if ! is_valid_booking_status COMPLETED; then fail "valid booking status was rejected"; else pass "valid booking status is accepted"; fi
if ! is_valid_payment_order_status PAID; then fail "valid payment status was rejected"; else pass "valid payment status is accepted"; fi

safe_output="$({
    export TEST_DB=skillswap_restore_test_case
    export POSTGRES_DB=skillswapdb
    export RESTORE_TARGET_ISOLATED=true
    bash "${SCRIPT_DIR}/db_restore_test.sh"
} 2>&1 || true)"
if [[ "$safe_output" == *"REQUIRED DEPENDENCY NOT AVAILABLE: pg_restore"* ]]; then
    pass "safe target proceeds to dependency checks"
else
    printf '%s\n' "$safe_output" >&2
    fail "safe target did not reach dependency checks"
fi

PROD_CONFIG="${SCRIPT_DIR}/../src/main/resources/application-prod.yml"
VALIDATOR="${SCRIPT_DIR}/../src/main/java/com/fptu/exe/skillswap/infrastructure/config/ProductionConfigurationValidator.java"
grep -Fq 'enabled: ${PRODUCTION_CONFIG_VALIDATION_ENABLED:true}' "$PROD_CONFIG" \
    || fail "production validation default is not fail-closed"
if grep -Fq 'ConditionalOnProperty' "$VALIDATOR"; then
    grep -Fq '@ConditionalOnProperty(prefix = "application.production-validation", name = "enabled", havingValue = "true")' "$VALIDATOR" \
        || fail "production validator condition is not fail-closed"
    grep -Fq 'PRODUCTION_CONFIG_VALIDATION_ENABLED:true' "$PROD_CONFIG" \
        || fail "production validation default is not true"
    grep -Fq 'PRODUCTION_CONFIG_VALIDATION_ENABLED: "true"' "${SCRIPT_DIR}/../docker-compose.prod.yml" \
        || fail "official production Compose path does not force validation"
    pass "production validator defaults on and official Compose forces it on"
else
    fail "production validator condition is missing; prod-profile test compatibility must be explicit"
fi

echo "Recovery safety regression checks passed."
