-- rollout: EXPAND
-- Optimize fetching reacted post IDs for the current user when browsing feed
CREATE INDEX IF NOT EXISTS idx_forum_post_reactions_user_post
    ON forum_post_reactions (user_id, post_id);

-- Optimize fetching reacted comment IDs for the current user when viewing comments
CREATE INDEX IF NOT EXISTS idx_forum_comment_reactions_user_comment
    ON forum_comment_reactions (user_id, comment_id);

-- Optimize querying comment replies/threads
CREATE INDEX IF NOT EXISTS idx_forum_comments_reply_to
    ON forum_comments (reply_to_comment_id)
    WHERE reply_to_comment_id IS NOT NULL;
