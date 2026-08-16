-- rollout: EXPAND
-- Migration V94: Support COURSE source type for Group Chat and link course enrollments to conversations
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'conversations'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%source_type%'
    ) LOOP
        EXECUTE 'ALTER TABLE conversations DROP CONSTRAINT ' || quote_ident(r.conname);
    END LOOP;
END $$;

ALTER TABLE conversations
    ADD CONSTRAINT conversations_source_type_check
    CHECK (source_type IN ('BOOKING', 'CLASS_OFFERING', 'COURSE'));
