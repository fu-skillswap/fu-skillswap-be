-- Add optimized indexes to support conversation listing recency ordering
-- and message history pagination query sorting.

CREATE INDEX IF NOT EXISTS idx_messages_conversation_created_at
    ON messages (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_conversations_last_message_at
    ON conversations (last_message_at DESC);
