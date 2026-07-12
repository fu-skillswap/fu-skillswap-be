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
        CHECK (status IN ('PENDING', 'REDIRECTED', 'SUCCEEDED', 'SUCCEEDED_SURPLUS', 'FAILED', 'CANCELLED', 'EXPIRED'));

    FOR constraint_record IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'credit_ledger_entries'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%PAYMENT_RESERVATION%'
    LOOP
        EXECUTE format('ALTER TABLE credit_ledger_entries DROP CONSTRAINT IF EXISTS %I', constraint_record.conname);
    END LOOP;

    ALTER TABLE credit_ledger_entries
        ADD CONSTRAINT credit_ledger_entries_origin_type_check
        CHECK (origin_type IN ('CAMPAIGN_BONUS', 'COUPON_BONUS', 'REFUND', 'MANUAL', 'PAYMENT_RESERVATION', 'PAYMENT_SURPLUS'));

    FOR constraint_record IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'credit_ledger_entries'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%PAYOUT_REQUEST%'
    LOOP
        EXECUTE format('ALTER TABLE credit_ledger_entries DROP CONSTRAINT IF EXISTS %I', constraint_record.conname);
    END LOOP;

    ALTER TABLE credit_ledger_entries
        ADD CONSTRAINT credit_ledger_entries_source_type_check
        CHECK (source_type IN ('PAYMENT_ORDER', 'BOOKING', 'CAMPAIGN', 'COUPON', 'MANUAL', 'PAYOUT_REQUEST', 'REFUND', 'PAYMENT_ATTEMPT'));
END $$;
