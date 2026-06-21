-- Remove MSSV verification mechanism entirely.
-- Student profiles keep the user-entered student code only, duplicates are allowed.

DROP INDEX IF EXISTS idx_student_profiles_verified_code;
ALTER TABLE student_profiles DROP COLUMN IF EXISTS verified_student_code;
