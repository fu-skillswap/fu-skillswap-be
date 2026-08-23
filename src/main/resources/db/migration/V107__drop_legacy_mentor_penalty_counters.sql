ALTER TABLE mentor_profiles
    DROP COLUMN IF EXISTS mentor_no_show_count,
    DROP COLUMN IF EXISTS mentor_completion_overdue_count,
    DROP COLUMN IF EXISTS late_cancellation_penalty_points;
