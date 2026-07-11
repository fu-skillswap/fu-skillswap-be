DROP INDEX IF EXISTS idx_forum_posts_status_last_activity_id;
CREATE INDEX idx_forum_posts_status_last_activity_id 
    ON forum_posts (status, last_activity_at DESC, id);

DROP INDEX IF EXISTS idx_forum_comments_post_status_created_id_asc;
CREATE INDEX idx_forum_comments_post_status_created_id_asc
    ON forum_comments (post_id, status, created_at ASC, id);

DROP INDEX IF EXISTS idx_forum_action_logs_user_action_created;
CREATE INDEX idx_forum_action_logs_user_action_created
    ON forum_action_logs (user_id, action_type, created_at DESC);

DROP INDEX IF EXISTS idx_forum_reports_status_created;
CREATE INDEX idx_forum_reports_status_created
    ON forum_reports (status, created_at ASC);
