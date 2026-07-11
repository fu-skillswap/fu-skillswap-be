UPDATE student_profiles
SET semester = 0
WHERE semester < 0;

UPDATE student_profiles
SET semester = 9
WHERE semester > 9;

UPDATE student_profiles
SET semester = 9
WHERE is_alumni = TRUE
  AND semester IS DISTINCT FROM 9;

ALTER TABLE student_profiles
    DROP CONSTRAINT IF EXISTS ck_student_profiles_semester_range;

ALTER TABLE student_profiles
    ADD CONSTRAINT ck_student_profiles_semester_range
        CHECK (semester IS NULL OR (semester >= 0 AND semester <= 9));
