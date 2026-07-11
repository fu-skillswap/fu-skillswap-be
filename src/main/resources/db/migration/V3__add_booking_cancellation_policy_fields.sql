ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS late_cancellation_penalty_points NUMERIC(6, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS booking_suspended_until TIMESTAMP;
