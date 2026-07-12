CREATE TABLE IF NOT EXISTS blog_categories (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(160) NOT NULL UNIQUE,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS blog_tags (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(140) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS blog_posts (
    id UUID PRIMARY KEY,
    author_user_id UUID NOT NULL,
    title VARCHAR(220) NOT NULL,
    slug VARCHAR(240) NOT NULL UNIQUE,
    slug_locked BOOLEAN NOT NULL DEFAULT FALSE,
    excerpt TEXT,
    content_markdown TEXT,
    content_hash VARCHAR(64),
    cover_image_url TEXT,
    cover_image_object_key TEXT,
    og_image_url TEXT,
    og_image_object_key TEXT,
    audience_type VARCHAR(30) NOT NULL DEFAULT 'BOTH',
    visibility VARCHAR(30) NOT NULL DEFAULT 'PUBLIC',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    seo_title VARCHAR(220),
    seo_description VARCHAR(320),
    canonical_url TEXT,
    reading_time_minutes INTEGER NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    featured_order INTEGER,
    featured_until TIMESTAMP,
    published_at TIMESTAMP,
    last_published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_blog_posts_author FOREIGN KEY (author_user_id) REFERENCES users(id),
    CONSTRAINT chk_blog_posts_audience_type CHECK (audience_type IN ('DEV', 'NON_TECH', 'BOTH')),
    CONSTRAINT chk_blog_posts_visibility CHECK (visibility IN ('PUBLIC', 'MEMBERS_ONLY', 'MENTOR_ONLY')),
    CONSTRAINT chk_blog_posts_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE TABLE IF NOT EXISTS blog_post_categories (
    post_id UUID NOT NULL,
    category_id UUID NOT NULL,
    PRIMARY KEY (post_id, category_id),
    CONSTRAINT fk_blog_post_categories_post FOREIGN KEY (post_id) REFERENCES blog_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_blog_post_categories_category FOREIGN KEY (category_id) REFERENCES blog_categories(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS blog_post_tags (
    post_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    PRIMARY KEY (post_id, tag_id),
    CONSTRAINT fk_blog_post_tags_post FOREIGN KEY (post_id) REFERENCES blog_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_blog_post_tags_tag FOREIGN KEY (tag_id) REFERENCES blog_tags(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_blog_posts_public_cursor
    ON blog_posts (status, visibility, published_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_blog_posts_admin_cursor
    ON blog_posts (updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_blog_posts_author_status
    ON blog_posts (author_user_id, status, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_blog_posts_featured
    ON blog_posts (status, is_featured, featured_order ASC, published_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_blog_post_categories_category
    ON blog_post_categories (category_id, post_id);

CREATE INDEX IF NOT EXISTS idx_blog_post_tags_tag
    ON blog_post_tags (tag_id, post_id);
