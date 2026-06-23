-- Flyway draft for target schema of Phase 1-4 Service-Centric MVP
-- NOTE: UUID generation should NOT use uuid_generate_v7() here as Hibernate/Java currently generates them via @GeneratedUuidV7 before INSERT.

-- 1. Add is_legacy to mentor_services
ALTER TABLE mentor_services ADD COLUMN IF NOT EXISTS is_legacy boolean NOT NULL DEFAULT false;

-- TODO: Backfill script to create legacy service and link it
-- INSERT INTO mentor_services (id, mentor_user_id, title, duration_minutes, is_free, price_amount, currency, is_active, is_legacy, created_at, updated_at)
-- SELECT <java_generated_uuid>, user_id, 'Tư vấn 1:1 (Legacy)', 60, true, 0, 'VND', true, true, NOW(), NOW() FROM mentor_profiles;

-- 2. Add service_id to mentor_availability_slots and rules
ALTER TABLE mentor_availability_slots ADD COLUMN IF NOT EXISTS service_id uuid;
ALTER TABLE mentor_availability_rules ADD COLUMN IF NOT EXISTS service_id uuid;

-- TODO: Backfill service_id on bookings and slots using the legacy service
-- UPDATE bookings SET service_id = (SELECT id FROM mentor_services WHERE mentor_user_id = bookings.mentor_user_id AND is_legacy = true LIMIT 1) WHERE service_id IS NULL;
-- UPDATE mentor_availability_slots SET service_id = (SELECT id FROM mentor_services WHERE mentor_user_id = mentor_availability_slots.mentor_user_id AND is_legacy = true LIMIT 1) WHERE service_id IS NULL;

-- 3. Add constraints
-- ALTER TABLE bookings ALTER COLUMN service_id SET NOT NULL;
-- ALTER TABLE mentor_availability_slots ALTER COLUMN service_id SET NOT NULL;
-- ALTER TABLE mentor_availability_slots ADD CONSTRAINT fk_availability_service FOREIGN KEY (service_id) REFERENCES mentor_services(id);

-- 4. Create sessions table
CREATE TABLE IF NOT EXISTS sessions (
    id uuid PRIMARY KEY,
    service_id uuid NOT NULL,
    mentor_user_id uuid NOT NULL,
    source_type varchar(50) NOT NULL,
    source_id uuid NOT NULL,
    scheduled_start_time timestamp NOT NULL,
    scheduled_end_time timestamp NOT NULL,
    actual_start_time timestamp,
    actual_end_time timestamp,
    meeting_platform varchar(50),
    meeting_link text,
    status varchar(50) NOT NULL,
    created_at timestamp NOT NULL,
    updated_at timestamp NOT NULL,
    CONSTRAINT uq_sessions_source UNIQUE (source_type, source_id),
    CONSTRAINT fk_sessions_service FOREIGN KEY (service_id) REFERENCES mentor_services(id),
    CONSTRAINT fk_sessions_mentor FOREIGN KEY (mentor_user_id) REFERENCES users(id)
);

-- 5. Create conversation tables
CREATE TABLE IF NOT EXISTS conversations (
    id uuid PRIMARY KEY,
    source_type varchar(50) NOT NULL,
    source_id uuid NOT NULL,
    type varchar(50) NOT NULL,
    status varchar(50) NOT NULL,
    lock_at timestamp,
    created_at timestamp NOT NULL,
    updated_at timestamp NOT NULL,
    CONSTRAINT uq_conversations_source UNIQUE (source_type, source_id)
);

CREATE TABLE IF NOT EXISTS conversation_participants (
    id uuid PRIMARY KEY,
    conversation_id uuid NOT NULL,
    user_id uuid NOT NULL,
    joined_at timestamp NOT NULL,
    CONSTRAINT uq_conv_participant UNIQUE (conversation_id, user_id),
    CONSTRAINT fk_conv_participants_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    CONSTRAINT fk_conv_participants_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS messages (
    id uuid PRIMARY KEY,
    conversation_id uuid NOT NULL,
    sender_id uuid,
    content text NOT NULL,
    message_type varchar(50) NOT NULL,
    created_at timestamp NOT NULL,
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id)
);
