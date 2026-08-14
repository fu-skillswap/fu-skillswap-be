-- rollout: EXPAND
-- CourseEnrollment.completedAt is used by the enrollment completion flow.
ALTER TABLE course_enrollments
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP(6) WITH TIME ZONE;
