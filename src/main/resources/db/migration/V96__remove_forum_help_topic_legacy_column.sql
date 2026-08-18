-- rollout: CONTRACT

ALTER TABLE forum_posts DROP CONSTRAINT IF EXISTS fk_forum_posts_help_topic;
DROP INDEX IF EXISTS idx_forum_posts_help_topic_created;
ALTER TABLE forum_posts DROP COLUMN IF EXISTS help_topic_id;
