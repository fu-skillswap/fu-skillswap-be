-- rollout: EXPAND
-- Immutable financial snapshots for administrator decisions on a booking dispute.
-- A later correction must append a linked reversal record; it must not overwrite this decision.
CREATE TABLE IF NOT EXISTS booking_issue_resolutions (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    resolved_by_user_id UUID NOT NULL,
    resolution_kind VARCHAR(20) NOT NULL,
    action VARCHAR(50) NOT NULL,
    reason_code VARCHAR(60) NOT NULL,
    admin_note TEXT,
    mentee_bps INTEGER,
    mentor_bps INTEGER,
    platform_bps INTEGER,
    escrow_scoin INTEGER NOT NULL DEFAULT 0,
    mentee_refund_scoin INTEGER NOT NULL DEFAULT 0,
    mentor_settlement_scoin INTEGER NOT NULL DEFAULT 0,
    platform_settlement_scoin INTEGER NOT NULL DEFAULT 0,
    settlement_applied_at_utc TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'APPLIED',
    reversal_of_resolution_id UUID,
    created_at_utc TIMESTAMPTZ NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_booking_issue_resolutions_booking
        FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT fk_booking_issue_resolutions_admin
        FOREIGN KEY (resolved_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_booking_issue_resolutions_reversal_of
        FOREIGN KEY (reversal_of_resolution_id) REFERENCES booking_issue_resolutions(id),
    CONSTRAINT chk_booking_issue_resolution_kind
        CHECK (resolution_kind IN ('RESOLUTION', 'REVERSAL')),
    CONSTRAINT chk_booking_issue_resolution_status
        CHECK (status IN ('APPLIED', 'REVERSED', 'MANUAL_FINANCE_REVIEW')),
    CONSTRAINT chk_booking_issue_resolution_bps
        CHECK (
            (mentee_bps IS NULL AND mentor_bps IS NULL AND platform_bps IS NULL)
            OR (mentee_bps BETWEEN 0 AND 10000
                AND mentor_bps BETWEEN 0 AND 10000
                AND platform_bps BETWEEN 0 AND 10000
                AND mentee_bps + mentor_bps + platform_bps = 10000)
        ),
    CONSTRAINT chk_booking_issue_resolution_amounts
        CHECK (escrow_scoin >= 0 AND mentee_refund_scoin >= 0
            AND mentor_settlement_scoin >= 0 AND platform_settlement_scoin >= 0)
);

CREATE INDEX IF NOT EXISTS idx_booking_issue_resolutions_booking_created
    ON booking_issue_resolutions (booking_id, created_at_utc DESC);

-- There can be only one applied decision for a dispute. A future reversal changes the original
-- record to REVERSED before a replacement decision is appended.
CREATE UNIQUE INDEX IF NOT EXISTS uq_booking_issue_resolution_active_decision
    ON booking_issue_resolutions (booking_id)
    WHERE resolution_kind = 'RESOLUTION' AND status = 'APPLIED';
