CREATE INDEX IF NOT EXISTS idx_forum_comments_post_status_created_id_asc
    ON forum_comments (post_id, status, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_forum_comments_status_created_id_desc
    ON forum_comments (status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_created_id_desc
    ON messages (conversation_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_conversations_last_message_id_desc
    ON conversations (last_message_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created_id_desc
    ON notifications (recipient_user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read_created_id_desc
    ON notifications (recipient_user_id, read_at, created_at DESC, id DESC);
