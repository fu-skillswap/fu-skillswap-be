-- rollout: EXPAND
-- Align persisted course summary fields with the Course aggregate mapping.
ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS total_chapters INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_lectures INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_duration_seconds INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS average_rating NUMERIC(3, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS review_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS enrolled_count INT NOT NULL DEFAULT 0;
