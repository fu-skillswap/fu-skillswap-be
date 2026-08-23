-- Deduplicate any duplicate (user_id, role) entries before adding primary key
DELETE FROM user_roles a
USING user_roles b
WHERE a.ctid < b.ctid
  AND a.user_id = b.user_id
  AND a.role = b.role;

-- Add Primary Key on (user_id, role) to optimize Eager Fetching by user_id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'pk_user_roles'
    ) THEN
        ALTER TABLE user_roles ADD CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role);
    END IF;
END $$;

-- Add Index on role column to optimize role-based search and filtering
CREATE INDEX IF NOT EXISTS idx_user_roles_role
    ON user_roles (role);
