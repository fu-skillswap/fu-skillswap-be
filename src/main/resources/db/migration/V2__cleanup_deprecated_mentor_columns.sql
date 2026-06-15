ALTER TABLE mentor_profiles
    DROP COLUMN IF EXISTS bio,
    DROP COLUMN IF EXISTS expertise_summary,
    DROP COLUMN IF EXISTS current_position,
    DROP COLUMN IF EXISTS current_company,
    DROP COLUMN IF EXISTS industry,
    DROP COLUMN IF EXISTS years_of_experience,
    DROP COLUMN IF EXISTS hourly_rate,
    DROP COLUMN IF EXISTS mentoring_style,
    DROP COLUMN IF EXISTS target_mentees;

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS total_rejected_bookings INTEGER NOT NULL DEFAULT 0;

ALTER TABLE mentor_verification_documents
    DROP COLUMN IF EXISTS is_primary;
