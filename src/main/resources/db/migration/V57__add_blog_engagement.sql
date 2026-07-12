ALTER TABLE blog_posts
    ADD COLUMN IF NOT EXISTS like_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS bookmark_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS blog_post_likes (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_blog_post_likes_post FOREIGN KEY (post_id) REFERENCES blog_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_blog_post_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_blog_post_likes_post_user UNIQUE (post_id, user_id)
);

CREATE TABLE IF NOT EXISTS blog_bookmarks (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_blog_bookmarks_post FOREIGN KEY (post_id) REFERENCES blog_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_blog_bookmarks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_blog_bookmarks_post_user UNIQUE (post_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_blog_post_likes_user_created
    ON blog_post_likes (user_id, created_at DESC, post_id DESC);

CREATE INDEX IF NOT EXISTS idx_blog_post_likes_post_created
    ON blog_post_likes (post_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_blog_bookmarks_user_cursor
    ON blog_bookmarks (user_id, created_at DESC, post_id DESC);

CREATE INDEX IF NOT EXISTS idx_blog_bookmarks_post_created
    ON blog_bookmarks (post_id, created_at DESC);
