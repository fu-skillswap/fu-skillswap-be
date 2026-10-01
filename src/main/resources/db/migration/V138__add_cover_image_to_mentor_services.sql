-- rollout: EXPAND
-- Add cover_file_id column to mentor_services table
ALTER TABLE mentor_services
    ADD COLUMN IF NOT EXISTS cover_file_id UUID,
    ADD CONSTRAINT fk_mentor_services_cover FOREIGN KEY (cover_file_id) REFERENCES files(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_mentor_services_cover_file_id ON mentor_services (cover_file_id);

-- Relax files purpose check to include SERVICE_COVER if constraint exists
ALTER TABLE files DROP CONSTRAINT IF EXISTS files_purpose_check;
ALTER TABLE files ADD CONSTRAINT files_purpose_check
    CHECK (purpose IN ('AVATAR','VERIFICATION_DOCUMENT','CERTIFICATE','PORTFOLIO','SESSION_ATTACHMENT','FORUM_ATTACHMENT','BLOG_IMAGE','SERVICE_COVER','OTHER'));
