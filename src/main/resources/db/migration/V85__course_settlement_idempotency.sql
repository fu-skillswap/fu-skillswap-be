-- rollout: EXPAND
-- V85__course_settlement_idempotency.sql
-- Guarantees one settlement allocation per (enrollment, session).
-- Existing duplicate financial allocations are NOT deleted, merged, or recalculated here:
-- resolving them requires business confirmation, so this migration fails loudly instead.

DO $$
DECLARE
    duplicate_pairs BIGINT;
BEGIN
    SELECT COUNT(*) INTO duplicate_pairs
    FROM (
        SELECT enrollment_id, course_session_id
        FROM course_enrollment_settlements
        GROUP BY enrollment_id, course_session_id
        HAVING COUNT(*) > 1
    ) AS duplicates;

    IF duplicate_pairs > 0 THEN
        RAISE EXCEPTION
            'Found % duplicate (enrollment_id, course_session_id) settlement pairs. Resolve them with business approval before applying V85.',
            duplicate_pairs;
    END IF;
END $$;

ALTER TABLE course_enrollment_settlements
    ADD CONSTRAINT uk_course_settlements_enrollment_session
    UNIQUE (enrollment_id, course_session_id);

CREATE INDEX IF NOT EXISTS idx_course_settlements_enrollment
    ON course_enrollment_settlements(enrollment_id);
