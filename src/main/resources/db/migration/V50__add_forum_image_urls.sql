ALTER TABLE forum_posts ADD COLUMN IF NOT EXISTS image_urls JSONB DEFAULT '[]'::jsonb;
ALTER TABLE forum_comments ADD COLUMN IF NOT EXISTS image_urls JSONB DEFAULT '[]'::jsonb;
