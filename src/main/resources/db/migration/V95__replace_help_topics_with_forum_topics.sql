-- rollout: EXPAND

CREATE TABLE forum_topics (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name_vi VARCHAR(100) NOT NULL,
    name_en VARCHAR(100) NOT NULL,
    display_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO forum_topics (id, code, name_vi, name_en, display_order, active)
VALUES
    ('00000000-0000-7000-8000-000000000001', 'QUESTION', 'Hỏi đáp', 'Questions', 1, TRUE),
    ('00000000-0000-7000-8000-000000000002', 'SHARING', 'Chia sẻ', 'Sharing', 2, TRUE),
    ('00000000-0000-7000-8000-000000000003', 'SEARCH', 'Tìm kiếm', 'Looking for', 3, TRUE),
    ('00000000-0000-7000-8000-000000000004', 'REVIEW', 'Review', 'Reviews', 4, TRUE)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE forum_posts ADD COLUMN forum_topic_id UUID;

UPDATE forum_posts
SET forum_topic_id = '00000000-0000-7000-8000-000000000001'
WHERE forum_topic_id IS NULL;

ALTER TABLE forum_posts
    ALTER COLUMN forum_topic_id SET NOT NULL,
    ADD CONSTRAINT fk_forum_posts_forum_topic
        FOREIGN KEY (forum_topic_id) REFERENCES forum_topics(id);

CREATE INDEX idx_forum_posts_forum_topic_created
    ON forum_posts (forum_topic_id, created_at);
