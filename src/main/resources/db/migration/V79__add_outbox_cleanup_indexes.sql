-- rollout: EXPAND
-- Flyway migration V79: Intentional application-level tuning & covering indexes for domain_event_outbox
CREATE INDEX IF NOT EXISTS idx_outbox_status_published_at ON domain_event_outbox (status, published_at, id);
CREATE INDEX IF NOT EXISTS idx_outbox_status_available_attempt ON domain_event_outbox (status, available_at, attempt_count, id);

-- Intentional table tuning note: Suggested autovacuum setting for high outbox deletion throughput.
-- DBAs may adjust autovacuum_vacuum_cost_limit based on production workload benchmarks.
ALTER TABLE domain_event_outbox SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_vacuum_cost_limit = 500
);
