-- rollout: EXPAND
-- Persist dispute SLA checkpoints so scheduler retries are idempotent and the admin queue
-- can explain exactly why a case is waiting, overdue, or eligible for final fallback.
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS admin_sla_overdue_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS admin_sla_reminder_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS admin_sla_last_reminder_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS admin_sla_auto_released_at_utc TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_bookings_under_review_admin_sla
    ON bookings (issue_human_review_escalated_at_utc, admin_sla_overdue_at_utc)
    WHERE status = 'UNDER_REVIEW' AND issue_resolved_at_utc IS NULL;
