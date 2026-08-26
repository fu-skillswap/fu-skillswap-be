-- rollout: EXPAND
-- Records the one-time escalation after 24 hours when a dispute cannot safely
-- be auto-resolved. It prevents duplicate admin email/notification delivery.
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS issue_human_review_escalated_at_utc TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_bookings_under_review_human_escalation
    ON bookings (issue_human_review_escalated_at_utc)
    WHERE status = 'UNDER_REVIEW' AND issue_human_review_escalated_at_utc IS NULL;
