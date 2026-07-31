-- rollout: EXPAND
-- Shared group session/chat experience and per-seat attendance. All new columns are additive.
ALTER TABLE conversation_participants
    ADD COLUMN IF NOT EXISTS participant_role varchar(32) NOT NULL DEFAULT 'DIRECT_PARTICIPANT',
    ADD COLUMN IF NOT EXISTS access_state varchar(32) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE conversation_participants
    DROP CONSTRAINT IF EXISTS chk_conversation_participant_role;
ALTER TABLE conversation_participants
    ADD CONSTRAINT chk_conversation_participant_role
        CHECK (participant_role IN ('MENTOR', 'ATTENDEE', 'DIRECT_PARTICIPANT'));

ALTER TABLE conversation_participants
    DROP CONSTRAINT IF EXISTS chk_conversation_participant_access;
ALTER TABLE conversation_participants
    ADD CONSTRAINT chk_conversation_participant_access
        CHECK (access_state IN ('ACTIVE', 'READ_ONLY', 'REVOKED'));

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS group_attendance_status varchar(32),
    ADD COLUMN IF NOT EXISTS group_attendance_marked_at timestamp(6),
    ADD COLUMN IF NOT EXISTS group_attendance_marked_by_user_id uuid;

ALTER TABLE bookings
    DROP CONSTRAINT IF EXISTS chk_bookings_group_attendance_status;
ALTER TABLE bookings
    ADD CONSTRAINT chk_bookings_group_attendance_status
        CHECK (group_attendance_status IS NULL OR group_attendance_status IN ('PRESENT', 'MENTEE_NO_SHOW'));

CREATE INDEX IF NOT EXISTS idx_bookings_group_attendance_deadline
    ON bookings (group_session_id, status, selected_end_time)
    WHERE group_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_conversation_participants_group_access
    ON conversation_participants (conversation_id, access_state);
