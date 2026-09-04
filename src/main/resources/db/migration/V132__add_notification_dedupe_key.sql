-- rollout: EXPAND
-- publishIfAbsent is used by retried background events. Make the read-then-insert
-- optimization safe across concurrent application instances.
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(200);

CREATE UNIQUE INDEX IF NOT EXISTS ux_notifications_dedupe_key
    ON notifications (dedupe_key)
    WHERE dedupe_key IS NOT NULL;
