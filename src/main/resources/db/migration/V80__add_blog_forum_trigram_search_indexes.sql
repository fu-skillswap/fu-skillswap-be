-- rollout: EXPAND
-- Accelerates the existing case-insensitive substring search contract.
-- Queries intentionally retain lower(column) LIKE '%keyword%' semantics.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Blog reader and admin search fields. Partial indexes exclude soft-deleted content.
CREATE INDEX IF NOT EXISTS idx_blog_posts_title_trgm
    ON blog_posts USING GIN (lower(title) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_blog_posts_excerpt_trgm
    ON blog_posts USING GIN (lower(coalesce(excerpt, '')) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_blog_posts_slug_trgm
    ON blog_posts USING GIN (lower(slug) gin_trgm_ops)
    WHERE deleted_at IS NULL;

-- Forum post/comment keyword search uses the same lower(...) LIKE pattern.
CREATE INDEX IF NOT EXISTS idx_forum_posts_title_trgm
    ON forum_posts USING GIN (lower(title) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_forum_posts_content_trgm
    ON forum_posts USING GIN (lower(content) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_forum_comments_content_trgm
    ON forum_comments USING GIN (lower(content) gin_trgm_ops)
    WHERE deleted_at IS NULL;

-- Both Blog and Forum search author names through lower(users.full_name).
CREATE INDEX IF NOT EXISTS idx_users_full_name_trgm
    ON users USING GIN (lower(full_name) gin_trgm_ops);
