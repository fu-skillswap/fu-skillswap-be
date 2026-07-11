-- Add optimized indexes to support stale pending booking cleanup scheduler
-- and mentee overlapping booking verification.

CREATE INDEX IF NOT EXISTS idx_bookings_status_requested_start_time
    ON bookings (status, requested_start_time);

CREATE INDEX IF NOT EXISTS idx_bookings_mentee_status_requested_time
    ON bookings (mentee_user_id, status, requested_start_time, requested_end_time);
