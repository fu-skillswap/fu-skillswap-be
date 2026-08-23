-- rollout: EXPAND
-- Migration V100: Add UNSUBMITTED event type to mentor_verification_request_events
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'mentor_verification_request_events'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%event_type%'
    ) LOOP
        EXECUTE 'ALTER TABLE mentor_verification_request_events DROP CONSTRAINT ' || quote_ident(r.conname);
    END LOOP;
END $$;

ALTER TABLE mentor_verification_request_events
    ADD CONSTRAINT mentor_verification_request_events_event_type_check
    CHECK (event_type IN (
        'REQUEST_CREATED',
        'SUBMITTED',
        'REVISION_REQUESTED',
        'RESUBMITTED',
        'APPROVED',
        'REJECTED',
        'WITHDRAWN',
        'UNSUBMITTED'
    ));
