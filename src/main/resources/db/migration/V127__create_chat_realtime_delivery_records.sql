-- rollout: EXPAND
-- Keep realtime fanout idempotent per durable outbox event and recipient.
CREATE TABLE IF NOT EXISTS chat_realtime_delivery_records (
    id uuid PRIMARY KEY,
    outbox_event_id uuid NOT NULL REFERENCES domain_event_outbox(id) ON DELETE CASCADE,
    recipient_user_id uuid NOT NULL REFERENCES users(id),
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    delivered_at timestamp(6),
    created_at timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT uq_chat_realtime_delivery_event_recipient UNIQUE (outbox_event_id, recipient_user_id),
    CONSTRAINT chk_chat_realtime_delivery_status CHECK (status IN ('PENDING', 'DELIVERED'))
);

CREATE INDEX IF NOT EXISTS idx_chat_realtime_delivery_event
    ON chat_realtime_delivery_records (outbox_event_id);
