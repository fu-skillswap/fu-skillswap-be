-- rollout: EXPAND
-- Current self-paced courses create one settlement allocation per enrollment.
-- Keep the legacy session column and foreign key for rolling-deploy compatibility,
-- but do not require a session value for new allocations.
ALTER TABLE course_enrollment_settlements
    ALTER COLUMN course_session_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_course_settlement_current_enrollment
    ON course_enrollment_settlements(enrollment_id)
    WHERE course_session_id IS NULL;
