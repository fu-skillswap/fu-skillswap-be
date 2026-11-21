-- rollout: EXPAND
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS avatar_file_id UUID,
    ADD COLUMN IF NOT EXISTS oauth_avatar_url TEXT;

ALTER TABLE users
    ADD CONSTRAINT fk_users_avatar_file
        FOREIGN KEY (avatar_file_id) REFERENCES files(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_users_avatar_file_id ON users (avatar_file_id);

UPDATE users u
SET oauth_avatar_url = u.avatar_url
WHERE u.oauth_avatar_url IS NULL
  AND u.avatar_url IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM oauth_accounts oa
      WHERE oa.user_id = u.id
        AND LOWER(oa.provider) = 'google'
  );
