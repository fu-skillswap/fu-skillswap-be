-- Drop the old unique constraint on student_code which was blocking multiple claims
ALTER TABLE student_profiles DROP CONSTRAINT IF EXISTS uq_student_profiles_code;

-- Add verified_student_code column (nullable)
ALTER TABLE student_profiles ADD COLUMN verified_student_code VARCHAR(30);

-- Add a partial unique index to ensure uniqueness only for verified codes
CREATE UNIQUE INDEX idx_student_profiles_verified_code ON student_profiles (verified_student_code) WHERE verified_student_code IS NOT NULL;
