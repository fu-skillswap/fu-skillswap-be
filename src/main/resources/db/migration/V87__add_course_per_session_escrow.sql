-- rollout: EXPAND
-- Immutable per-session escrow allocations. Buyer fees are non-refundable for learner-initiated refunds.

ALTER TABLE course_enrollment_settlements
    DROP COLUMN IF EXISTS platform_fee_scoin,
    ADD COLUMN IF NOT EXISTS base_price_scoin INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS buyer_fee_scoin INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS mentor_commission_scoin INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS platform_revenue_scoin INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS student_refundable_scoin INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS refund_reason VARCHAR(120),
    ADD COLUMN IF NOT EXISTS release_operation_key VARCHAR(160),
    ADD COLUMN IF NOT EXISTS refund_operation_key VARCHAR(160),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE course_enrollment_settlements
    ADD CONSTRAINT chk_course_settlement_non_negative
    CHECK (base_price_scoin >= 0 AND buyer_fee_scoin >= 0 AND mentor_commission_scoin >= 0
       AND mentor_payout_scoin >= 0 AND platform_revenue_scoin >= 0 AND student_refundable_scoin >= 0);

ALTER TABLE course_enrollment_settlements
    ADD CONSTRAINT chk_course_settlement_status
    CHECK (status IN ('HELD', 'ELIGIBLE', 'RELEASED', 'REFUNDED'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_course_settlement_release_operation
    ON course_enrollment_settlements(release_operation_key)
    WHERE release_operation_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_course_settlement_refund_operation
    ON course_enrollment_settlements(refund_operation_key)
    WHERE refund_operation_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_course_settlement_eligible
    ON course_enrollment_settlements(status, eligible_at);

CREATE INDEX IF NOT EXISTS idx_course_settlement_enrollment_status
    ON course_enrollment_settlements(enrollment_id, status);
