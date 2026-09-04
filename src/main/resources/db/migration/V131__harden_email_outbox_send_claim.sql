-- rollout: EXPAND
-- Email provider calls happen outside the database transaction. SENDING is a durable
-- in-flight marker so a second application instance cannot claim the same row.
ALTER TABLE email_outbox
    ADD COLUMN IF NOT EXISTS sending_started_at TIMESTAMP(6);

ALTER TABLE email_outbox
    DROP CONSTRAINT IF EXISTS email_outbox_status_check;

ALTER TABLE email_outbox
    DROP CONSTRAINT IF EXISTS email_outbox_status_check1;

-- Existing installations may have an unnamed Hibernate check constraint. Drop any
-- email_outbox status check before installing the complete current enum set.
DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'email_outbox'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE email_outbox DROP CONSTRAINT IF EXISTS %I', constraint_record.conname);
    END LOOP;
END $$;

ALTER TABLE email_outbox
    ADD CONSTRAINT email_outbox_status_check
    CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'READ', 'FATAL_ERROR'));

CREATE INDEX IF NOT EXISTS idx_email_outbox_sending_started
    ON email_outbox (status, sending_started_at);
