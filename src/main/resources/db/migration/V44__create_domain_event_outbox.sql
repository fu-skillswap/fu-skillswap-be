CREATE TABLE IF NOT EXISTS domain_event_outbox (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(120) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    available_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_domain_event_outbox_status_available_created
    ON domain_event_outbox (status, available_at, created_at);

CREATE INDEX IF NOT EXISTS idx_domain_event_outbox_aggregate_created
    ON domain_event_outbox (aggregate_type, aggregate_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_domain_event_outbox_event_type_created
    ON domain_event_outbox (event_type, created_at DESC);
