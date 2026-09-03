#!/bin/bash
# Shared, side-effect-free guards for database recovery drills.
# This file is sourced by recovery scripts and local safety tests.

restore_drill_error() {
    echo "FAIL - $1" >&2
}

validate_restore_target() {
    local test_db="${TEST_DB:-}"
    local production_db="${PRODUCTION_DB_NAME:-${POSTGRES_DB:-}}"
    local target_host="${RESTORE_TARGET_HOST:-${PGHOST:-${POSTGRES_HOST:-}}}"
    local production_host="${PRODUCTION_DB_HOST:-${DATABASE_HOST:-}}"
    local normalized_test_db
    local normalized_production_db
    local normalized_target_host
    local normalized_production_host

    if [[ -z "$test_db" ]]; then
        restore_drill_error "TEST_DB is required; refusing to use an implicit database name."
        return 1
    fi
    if [[ ! "$test_db" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]]; then
        restore_drill_error "TEST_DB must be a simple PostgreSQL identifier."
        return 1
    fi
    if [[ ! "$test_db" == skillswap_restore_test_* ]]; then
        restore_drill_error "TEST_DB must use the isolated skillswap_restore_test_ prefix."
        return 1
    fi
    if [[ -z "$production_db" ]]; then
        restore_drill_error "POSTGRES_DB or PRODUCTION_DB_NAME is required for target comparison."
        return 1
    fi

    normalized_test_db="${test_db,,}"
    normalized_production_db="${production_db,,}"
    if [[ "$normalized_test_db" == "$normalized_production_db" ]]; then
        restore_drill_error "TEST_DB matches the configured production database name."
        return 1
    fi

    case "$normalized_test_db" in
        *prod*|*production*|*live*|*primary*)
            restore_drill_error "TEST_DB matches a protected production-name pattern."
            return 1
            ;;
    esac

    if [[ "${RESTORE_TARGET_ISOLATED:-}" != "true" ]]; then
        restore_drill_error "RESTORE_TARGET_ISOLATED=true is required for a recovery drill."
        return 1
    fi

    normalized_target_host="${target_host,,}"
    normalized_production_host="${production_host,,}"
    if [[ -n "$normalized_production_host" && "$normalized_target_host" == "$normalized_production_host" ]]; then
        restore_drill_error "restore target host matches the configured production database host."
        return 1
    fi
    case "$normalized_target_host" in
        *prod*|*production*|*live*|*primary*)
            restore_drill_error "restore target host matches a protected production-host pattern."
            return 1
            ;;
    esac

    echo "PASS - isolated restore target accepted: ${TEST_DB}"
}

# These lists intentionally mirror the current Java enums and the validated
# database CHECK constraints. A backup can omit or lose constraints, so the
# drill must retain an explicit independent state check.
BOOKING_STATUS_SQL="'PENDING','ACCEPTED_AWAITING_PAYMENT','PAID','REJECTED','EXPIRED','CANCELLED_BY_MENTEE','CANCELLED_BY_MENTOR','AWAITING_MENTOR_COMPLETION','AWAITING_MENTEE_CONFIRMATION','COMPLETED','UNDER_REVIEW'"
PAYMENT_ORDER_STATUS_SQL="'PENDING','PARTIALLY_COVERED_BY_CREDIT','AWAITING_PROVIDER_PAYMENT','PAID','FAILED','CANCELLED','EXPIRED'"

is_valid_booking_status() {
    case "$1" in
        PENDING|ACCEPTED_AWAITING_PAYMENT|PAID|REJECTED|EXPIRED|CANCELLED_BY_MENTEE|CANCELLED_BY_MENTOR|AWAITING_MENTOR_COMPLETION|AWAITING_MENTEE_CONFIRMATION|COMPLETED|UNDER_REVIEW) return 0 ;;
        *) return 1 ;;
    esac
}

is_valid_payment_order_status() {
    case "$1" in
        PENDING|PARTIALLY_COVERED_BY_CREDIT|AWAITING_PROVIDER_PAYMENT|PAID|FAILED|CANCELLED|EXPIRED) return 0 ;;
        *) return 1 ;;
    esac
}
