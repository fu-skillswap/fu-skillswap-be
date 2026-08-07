-- rollout: EXPAND
-- Durable provider-attempt, course-worker and optimistic-concurrency support.

DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'payment_attempts'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%SUCCEEDED%'
    LOOP
        EXECUTE format('ALTER TABLE payment_attempts DROP CONSTRAINT IF EXISTS %I', constraint_record.conname);
    END LOOP;
    ALTER TABLE payment_attempts
        ADD CONSTRAINT payment_attempts_status_check
        CHECK (status IN ('CREATING', 'CREATED', 'PENDING', 'REDIRECTED', 'SUCCEEDED',
                          'SUCCEEDED_SURPLUS', 'FAILED', 'CANCELLED', 'EXPIRED'));
END $$;

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE bunny_webhook_events
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE course_outbox_events
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP(6) WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_bunny_webhook_claim
    ON bunny_webhook_events(status, next_retry_at, processing_started_at, received_at);

CREATE INDEX IF NOT EXISTS idx_course_outbox_claim
    ON course_outbox_events(status, next_retry_at, processing_started_at, created_at);
