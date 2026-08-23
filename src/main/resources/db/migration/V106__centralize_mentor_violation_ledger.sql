-- rollout: EXPAND
ALTER TABLE mentor_violation_events
    ADD COLUMN source_module VARCHAR(30),
    ADD COLUMN source_reference_id UUID,
    ADD COLUMN severity VARCHAR(20),
    ADD COLUMN decision_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN decision_note VARCHAR(1000),
    ADD COLUMN reversed_at TIMESTAMP,
    ADD COLUMN reversed_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN reversal_reason VARCHAR(500);

UPDATE mentor_violation_events
SET source_module = 'BOOKING',
    source_reference_id = booking_id,
    severity = CASE violation_type
        WHEN 'LATE_CANCELLATION' THEN 'LOW'
        WHEN 'COMPLETION_OVERDUE' THEN 'MEDIUM'
        ELSE 'HIGH'
    END
WHERE source_module IS NULL;

ALTER TABLE mentor_violation_events
    ALTER COLUMN source_module SET NOT NULL,
    ALTER COLUMN severity SET NOT NULL;

ALTER TABLE mentor_violation_events
    DROP CONSTRAINT mentor_violation_type_check;

ALTER TABLE mentor_violation_events
    ADD CONSTRAINT mentor_violation_type_check CHECK (violation_type IN (
        'LATE_CANCELLATION', 'COMPLETION_OVERDUE', 'MENTOR_NO_SHOW',
        'BOOKING_POLICY_BREACH', 'CHAT_POLICY_BREACH', 'FORUM_POLICY_BREACH',
        'VERIFICATION_FRAUD', 'ADMIN_CONFIRMED_BREACH'
    )),
    ADD CONSTRAINT mentor_violation_source_check CHECK (source_module IN ('BOOKING', 'CHAT', 'FORUM', 'VERIFICATION', 'ADMIN')),
    ADD CONSTRAINT mentor_violation_severity_check CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

CREATE INDEX idx_mentor_violation_source_reference
    ON mentor_violation_events (source_module, source_reference_id)
    WHERE source_reference_id IS NOT NULL;
