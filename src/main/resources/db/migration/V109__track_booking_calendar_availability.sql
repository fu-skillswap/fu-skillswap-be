-- rollout: EXPAND
-- Keep booking in the database as the source of truth when Google Calendar is temporarily unavailable.
ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS calendar_availability_unknown boolean NOT NULL DEFAULT false;

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS calendar_availability_checked_at timestamp;
