-- rollout: EXPAND
-- Storage-retention indexes and the durable notification archive manifest.

ALTER TABLE user_sessions
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP(6);

CREATE INDEX IF NOT EXISTS idx_user_sessions_state_expiry
    ON user_sessions (session_state, expires_at);

CREATE INDEX IF NOT EXISTS idx_user_sessions_expires_at
    ON user_sessions (expires_at);

CREATE TABLE IF NOT EXISTS notification_archive_manifests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    period_start TIMESTAMP(6) NOT NULL,
    period_end TIMESTAMP(6) NOT NULL,
    storage_key TEXT NOT NULL UNIQUE,
    checksum VARCHAR(64) NOT NULL,
    record_count INTEGER NOT NULL CHECK (record_count > 0),
    created_at TIMESTAMP(6) NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notification_archive_manifests_user_period
    ON notification_archive_manifests (user_id, period_start DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_read_created
    ON notifications (read_at, created_at);

CREATE INDEX IF NOT EXISTS idx_chat_attachments_cleanup
    ON chat_attachments (state, expires_at, deleted_at, hold_until);

ALTER TABLE messages SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_vacuum_cost_limit = 2000
);

ALTER TABLE notifications SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_vacuum_cost_limit = 2000
);

ALTER TABLE audit_logs SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_vacuum_cost_limit = 2000
);

ALTER TABLE internal_telemetry_events SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_vacuum_cost_limit = 2000
);
