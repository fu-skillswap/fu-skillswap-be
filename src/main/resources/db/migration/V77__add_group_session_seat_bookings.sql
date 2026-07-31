-- rollout: EXPAND
-- Group seats reuse bookings and payment orders; direct bookings keep group_session_id null.
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS group_session_id uuid REFERENCES group_sessions(id);

CREATE INDEX IF NOT EXISTS idx_bookings_group_session_status
    ON bookings (group_session_id, status)
    WHERE group_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_group_sessions_discovery
    ON group_sessions (status, registration_status, scheduled_start_at);

CREATE UNIQUE INDEX IF NOT EXISTS uq_group_session_active_seat_per_mentee
    ON bookings (mentee_user_id, group_session_id)
    WHERE group_session_id IS NOT NULL
      AND status IN ('ACCEPTED_AWAITING_PAYMENT', 'ACCEPTED', 'PAID',
                     'AWAITING_MENTOR_COMPLETION', 'AWAITING_MENTEE_CONFIRMATION', 'UNDER_REVIEW');
