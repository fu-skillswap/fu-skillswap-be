ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS reschedule_count INT NOT NULL DEFAULT 0;

ALTER TABLE booking_reschedule_requests
    ADD COLUMN IF NOT EXISTS responded_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS responder_role VARCHAR(20),
    ADD COLUMN IF NOT EXISTS previous_selected_start_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS previous_selected_end_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS proposed_selected_start_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS proposed_selected_end_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS expired_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS admin_override BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE booking_reschedule_requests
SET previous_selected_start_time = COALESCE(previous_selected_start_time, requested_at),
    previous_selected_end_time = COALESCE(previous_selected_end_time, requested_at),
    proposed_selected_start_time = COALESCE(proposed_selected_start_time, requested_at),
    proposed_selected_end_time = COALESCE(proposed_selected_end_time, requested_at)
WHERE previous_selected_start_time IS NULL
   OR previous_selected_end_time IS NULL
   OR proposed_selected_start_time IS NULL
   OR proposed_selected_end_time IS NULL;

ALTER TABLE booking_reschedule_requests
    ALTER COLUMN previous_selected_start_time SET NOT NULL,
    ALTER COLUMN previous_selected_end_time SET NOT NULL,
    ALTER COLUMN proposed_selected_start_time SET NOT NULL,
    ALTER COLUMN proposed_selected_end_time SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_booking_reschedule_requested_by
    ON booking_reschedule_requests (requested_by_user_id);

CREATE INDEX IF NOT EXISTS idx_booking_reschedule_responded_by
    ON booking_reschedule_requests (responded_by_user_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_booking_reschedule_one_pending_per_booking
    ON booking_reschedule_requests (booking_id)
    WHERE status = 'PENDING';
