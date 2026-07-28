-- rollout: EXPAND
ALTER TABLE conversation_participants
    ADD COLUMN IF NOT EXISTS last_read_sequence bigint NOT NULL DEFAULT 0;

UPDATE conversation_participants cp
SET last_read_sequence = COALESCE((
    SELECT max(m.sequence) FROM messages m
    WHERE m.conversation_id = cp.conversation_id
      AND cp.last_read_at IS NOT NULL
      AND m.created_at <= cp.last_read_at
), 0);

ALTER TABLE chat_attachments
    ADD COLUMN IF NOT EXISTS state varchar(20) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_messages_conversation_sequence_desc
    ON messages(conversation_id, sequence DESC);
CREATE INDEX IF NOT EXISTS idx_chat_attachments_message ON chat_attachments(message_id);
