-- rollout: EXPAND
-- V84__course_subsystem_production_hardening.sql

-- 1. Modify bunny_webhook_events
ALTER TABLE bunny_webhook_events ADD COLUMN raw_payload TEXT;
ALTER TABLE bunny_webhook_events ADD COLUMN payload_hash VARCHAR(64);
ALTER TABLE bunny_webhook_events ADD COLUMN next_retry_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE bunny_webhook_events RENAME COLUMN error_message TO last_error;

-- 2. Modify course_materials
ALTER TABLE course_materials ADD COLUMN deleted_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE course_materials ADD COLUMN delete_requested_at TIMESTAMP(6) WITH TIME ZONE;

-- 3. Modify course_enrollments
ALTER TABLE course_enrollments ADD COLUMN base_price_scoin INT NOT NULL DEFAULT 0;
ALTER TABLE course_enrollments ADD COLUMN buyer_fee_scoin INT NOT NULL DEFAULT 0;
ALTER TABLE course_enrollments ADD COLUMN mentor_commission_scoin INT NOT NULL DEFAULT 0;
ALTER TABLE course_enrollments ADD COLUMN mentor_payout_scoin INT NOT NULL DEFAULT 0;

-- 4. Modify course_enrollment_settlements
ALTER TABLE course_enrollment_settlements RENAME COLUMN allocated_scoin TO mentor_payout_scoin;
ALTER TABLE course_enrollment_settlements ADD COLUMN platform_fee_scoin INT NOT NULL DEFAULT 0;

-- 5. Create generic outbox table for course subsystem
CREATE TABLE course_outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(120) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP(6) WITH TIME ZONE,
    last_error TEXT,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_course_outbox_status_retry ON course_outbox_events(status, next_retry_at);
