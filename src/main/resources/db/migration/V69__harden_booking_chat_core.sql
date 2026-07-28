-- rollout: EXPAND
-- Booking chat is a durable mentor/mentee relationship. Booking links and access are derived separately.
ALTER TABLE mentor_services
    ADD COLUMN IF NOT EXISTS maintain_post_session_chat boolean NOT NULL DEFAULT false;

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS maintain_post_session_chat_snapshot boolean NOT NULL DEFAULT false;

ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS mentor_user_id uuid,
    ADD COLUMN IF NOT EXISTS mentee_user_id uuid,
    ADD COLUMN IF NOT EXISTS next_sequence bigint NOT NULL DEFAULT 0;

-- Legacy booking-backed conversations obtain their role-aware direct-pair identity.
UPDATE conversations c
SET mentor_user_id = b.mentor_user_id,
    mentee_user_id = b.mentee_user_id
FROM bookings b
WHERE c.source_type = 'BOOKING'
  AND c.source_id = b.id
  AND (c.mentor_user_id IS NULL OR c.mentee_user_id IS NULL);

CREATE TABLE IF NOT EXISTS conversation_booking_links (
    id uuid PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES conversations(id),
    booking_id uuid NOT NULL REFERENCES bookings(id),
    linked_at timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT uq_conversation_booking_link_pair UNIQUE (conversation_id, booking_id),
    CONSTRAINT uq_conversation_booking_link_booking UNIQUE (booking_id)
);

-- Pre-launch merge: retain the earliest direct thread for a mentor/mentee pair before adding uniqueness.
WITH ranked AS (
    SELECT id,
           first_value(id) OVER (
               PARTITION BY mentor_user_id, mentee_user_id
               ORDER BY created_at, id
           ) AS canonical_id,
           row_number() OVER (
               PARTITION BY mentor_user_id, mentee_user_id
               ORDER BY created_at, id
           ) AS row_num
    FROM conversations
    WHERE mentor_user_id IS NOT NULL AND mentee_user_id IS NOT NULL
)
UPDATE messages m
SET conversation_id = ranked.canonical_id
FROM ranked
WHERE m.conversation_id = ranked.id AND ranked.row_num > 1;

WITH ranked AS (
    SELECT id,
           row_number() OVER (PARTITION BY mentor_user_id, mentee_user_id ORDER BY created_at, id) AS row_num
    FROM conversations
    WHERE mentor_user_id IS NOT NULL AND mentee_user_id IS NOT NULL
)
DELETE FROM conversation_participants cp
USING ranked
WHERE cp.conversation_id = ranked.id AND ranked.row_num > 1;

WITH ranked AS (
    SELECT id,
           first_value(id) OVER (PARTITION BY mentor_user_id, mentee_user_id ORDER BY created_at, id) AS canonical_id,
           row_number() OVER (PARTITION BY mentor_user_id, mentee_user_id ORDER BY created_at, id) AS row_num
    FROM conversations
    WHERE mentor_user_id IS NOT NULL AND mentee_user_id IS NOT NULL
)
DELETE FROM conversations c
USING ranked
WHERE c.id = ranked.id AND ranked.row_num > 1;

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS sequence bigint,
    ADD COLUMN IF NOT EXISTS client_message_id uuid,
    ADD COLUMN IF NOT EXISTS request_hash varchar(64),
    ADD COLUMN IF NOT EXISTS reply_to_message_id uuid,
    ADD COLUMN IF NOT EXISTS message_state varchar(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS edited_at timestamp(6),
    ADD COLUMN IF NOT EXISTS deleted_at timestamp(6),
    ADD COLUMN IF NOT EXISTS deleted_by_user_id uuid REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS booking_id uuid REFERENCES bookings(id),
    ADD COLUMN IF NOT EXISTS system_event_type varchar(50);

-- Existing data is ordered deterministically before enforcing conversation-local sequencing.
WITH numbered AS (
    SELECT id, row_number() OVER (PARTITION BY conversation_id ORDER BY created_at, id) AS value
    FROM messages
)
UPDATE messages m SET sequence = numbered.value FROM numbered WHERE numbered.id = m.id AND m.sequence IS NULL;

UPDATE conversations c
SET next_sequence = COALESCE((SELECT max(m.sequence) FROM messages m WHERE m.conversation_id = c.id), 0);

INSERT INTO conversation_booking_links (id, conversation_id, booking_id)
SELECT id, id, source_id FROM conversations WHERE source_type = 'BOOKING'
ON CONFLICT (booking_id) DO NOTHING;

CREATE UNIQUE INDEX IF NOT EXISTS uq_messages_conversation_sequence
    ON messages(conversation_id, sequence);
CREATE UNIQUE INDEX IF NOT EXISTS uq_messages_client_message
    ON messages(conversation_id, sender_id, client_message_id)
    WHERE client_message_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_messages_booking_system_event
    ON messages(booking_id, system_event_type)
    WHERE booking_id IS NOT NULL AND system_event_type IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_conversation_booking_links_booking ON conversation_booking_links(booking_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_conversations_direct_pair
    ON conversations(mentor_user_id, mentee_user_id)
    WHERE mentor_user_id IS NOT NULL AND mentee_user_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS chat_upload_intents (
    id uuid PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES conversations(id),
    owner_user_id uuid NOT NULL REFERENCES users(id),
    storage_key varchar(600) NOT NULL UNIQUE,
    original_filename varchar(255) NOT NULL,
    content_type varchar(150) NOT NULL,
    expected_size_bytes bigint NOT NULL,
    status varchar(30) NOT NULL,
    expires_at timestamp(6) NOT NULL,
    created_at timestamp(6) NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_upload_intents_expiry ON chat_upload_intents(status, expires_at);

CREATE TABLE IF NOT EXISTS chat_attachments (
    id uuid PRIMARY KEY,
    message_id uuid NOT NULL REFERENCES messages(id),
    upload_intent_id uuid NOT NULL UNIQUE REFERENCES chat_upload_intents(id),
    storage_key varchar(600) NOT NULL UNIQUE,
    original_filename varchar(255) NOT NULL,
    content_type varchar(150) NOT NULL,
    size_bytes bigint NOT NULL,
    expires_at timestamp(6) NOT NULL,
    revoked_at timestamp(6),
    deleted_at timestamp(6),
    hold_until timestamp(6),
    created_at timestamp(6) NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_attachments_expiry ON chat_attachments(expires_at, deleted_at, hold_until);
