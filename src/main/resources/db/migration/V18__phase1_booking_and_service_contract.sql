ALTER TABLE mentor_services
    ADD COLUMN IF NOT EXISTS expected_outcome TEXT;

UPDATE mentor_services
SET expected_outcome = COALESCE(NULLIF(description, ''), 'Kết quả kỳ vọng sẽ được mentor cập nhật chi tiết.')
WHERE expected_outcome IS NULL;

ALTER TABLE mentor_services
    ALTER COLUMN expected_outcome SET NOT NULL;

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS selected_start_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS selected_end_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS service_title_snapshot VARCHAR(200),
    ADD COLUMN IF NOT EXISTS service_description_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS service_duration_snapshot INTEGER,
    ADD COLUMN IF NOT EXISTS service_expected_outcome_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS service_is_free_snapshot BOOLEAN,
    ADD COLUMN IF NOT EXISTS service_price_amount_snapshot NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS service_currency_snapshot VARCHAR(10),
    ADD COLUMN IF NOT EXISTS finalized_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS auto_closed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS completion_outcome VARCHAR(50),
    ADD COLUMN IF NOT EXISTS issue_submitted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS issue_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS issue_description TEXT,
    ADD COLUMN IF NOT EXISTS wants_admin_review BOOLEAN;

UPDATE bookings
SET selected_start_time = COALESCE(selected_start_time, requested_start_time),
    selected_end_time = COALESCE(selected_end_time, requested_end_time)
WHERE selected_start_time IS NULL
   OR selected_end_time IS NULL;

CREATE INDEX IF NOT EXISTS idx_bookings_status_selected_start_time
    ON bookings (status, selected_start_time);

CREATE INDEX IF NOT EXISTS idx_bookings_mentee_status_selected_time
    ON bookings (mentee_user_id, status, selected_start_time, selected_end_time);
