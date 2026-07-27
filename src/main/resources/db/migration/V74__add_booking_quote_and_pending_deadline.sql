ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS pending_expire_at TIMESTAMP;

-- Backfill only the deadline. The scheduler owns any subsequent lifecycle transition.
UPDATE bookings
SET pending_expire_at = CASE
    WHEN selected_start_time IS NULL THEN created_at + INTERVAL '12 hours'
    ELSE LEAST(created_at + INTERVAL '12 hours', selected_start_time - INTERVAL '3 hours')
END
WHERE status = 'PENDING'
  AND pending_expire_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_bookings_status_pending_expire_at
    ON bookings (status, pending_expire_at);
