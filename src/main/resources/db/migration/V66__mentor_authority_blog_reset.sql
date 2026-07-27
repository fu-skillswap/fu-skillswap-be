-- Pre-launch Blog contract reset. Preserve restricted legacy content rather than broadening access.
UPDATE blog_posts
SET status = 'ARCHIVED', updated_at = NOW()
WHERE visibility = 'MENTOR_ONLY'
  AND deleted_at IS NULL;

UPDATE blog_posts
SET visibility = 'AUTHENTICATED', updated_at = NOW()
WHERE visibility = 'MEMBERS_ONLY';

ALTER TABLE blog_posts
    ADD COLUMN IF NOT EXISTS author_type VARCHAR(30) NOT NULL DEFAULT 'PLATFORM';

ALTER TABLE blog_posts
    DROP CONSTRAINT IF EXISTS chk_blog_posts_audience_type;

ALTER TABLE blog_posts
    DROP COLUMN IF EXISTS audience_type;

ALTER TABLE blog_posts
    DROP CONSTRAINT IF EXISTS chk_blog_posts_visibility;

ALTER TABLE blog_posts
    ADD CONSTRAINT chk_blog_posts_visibility
        CHECK (visibility IN ('PUBLIC', 'AUTHENTICATED', 'BOOKED_MEMBERS'));

CREATE TABLE IF NOT EXISTS blog_post_entitled_services (
    post_id UUID NOT NULL REFERENCES blog_posts(id) ON DELETE CASCADE,
    service_id UUID NOT NULL REFERENCES mentor_services(id),
    PRIMARY KEY (post_id, service_id)
);

CREATE INDEX IF NOT EXISTS idx_blog_post_entitled_services_service
    ON blog_post_entitled_services (service_id, post_id);

CREATE TABLE IF NOT EXISTS blog_mentor_follows (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mentor_user_id UUID NOT NULL REFERENCES mentor_profiles(user_id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_blog_mentor_follows_user_mentor UNIQUE (user_id, mentor_user_id)
);

CREATE INDEX IF NOT EXISTS idx_blog_mentor_follows_user_created
    ON blog_mentor_follows (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_blog_mentor_follows_mentor_user
    ON blog_mentor_follows (mentor_user_id, user_id);

DROP TABLE IF EXISTS blog_tag_follows;
