-- Attendance is an append-only, server-timestamped declaration by a mentoring participant.
-- It is intentionally independent from settlement and does not backfill historical sessions.
CREATE TABLE IF NOT EXISTS session_attendances (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    participant_role VARCHAR(20) NOT NULL,
    participant_user_id UUID NOT NULL,
    checked_in_at_utc TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_session_attendances_session
        FOREIGN KEY (session_id) REFERENCES sessions(id),
    CONSTRAINT fk_session_attendances_user
        FOREIGN KEY (participant_user_id) REFERENCES users(id),
    CONSTRAINT uq_session_attendances_session_role
        UNIQUE (session_id, participant_role),
    CONSTRAINT chk_session_attendances_participant_role
        CHECK (participant_role IN ('MENTOR', 'MENTEE'))
);

CREATE INDEX IF NOT EXISTS idx_session_attendances_session
    ON session_attendances(session_id);
CREATE INDEX IF NOT EXISTS idx_session_attendances_participant_time
    ON session_attendances(participant_user_id, checked_in_at_utc DESC);
