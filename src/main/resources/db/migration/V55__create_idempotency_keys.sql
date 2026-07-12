-- Rollback Note:
-- Destructive: No.
-- Rollback Strategy: App rollback is possible. If needed, DB rollback can be done manually via:
-- DROP TABLE idempotency_keys;
-- Affect on old data: New table only, no old data is changed.

CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    method VARCHAR(10) NOT NULL,
    path VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_created_at
ON idempotency_keys(created_at);
