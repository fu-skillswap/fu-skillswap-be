CREATE TABLE mentor_violation_events (
    id UUID PRIMARY KEY,
    mentor_user_id UUID NOT NULL REFERENCES mentor_profiles(user_id) ON DELETE CASCADE,
    booking_id UUID REFERENCES bookings(id) ON DELETE SET NULL,
    violation_type VARCHAR(60) NOT NULL,
    points NUMERIC(8, 2) NOT NULL CHECK (points > 0),
    reason VARCHAR(500) NOT NULL,
    operation_key VARCHAR(160) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_mentor_violation_operation UNIQUE (operation_key),
    CONSTRAINT mentor_violation_type_check CHECK (
        violation_type IN (
            'LATE_CANCELLATION',
            'COMPLETION_OVERDUE',
            'MENTOR_NO_SHOW'
        )
    )
);

CREATE INDEX idx_mentor_violation_mentor_time
    ON mentor_violation_events (mentor_user_id, occurred_at DESC);

CREATE INDEX idx_mentor_violation_booking
    ON mentor_violation_events (booking_id)
    WHERE booking_id IS NOT NULL;
