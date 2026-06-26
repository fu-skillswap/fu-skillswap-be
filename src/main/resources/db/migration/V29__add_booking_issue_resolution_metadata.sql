ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS issue_resolved_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS issue_resolved_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS issue_resolution_note TEXT;
