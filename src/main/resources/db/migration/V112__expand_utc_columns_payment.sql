-- rollout: EXPAND
-- Slice 5: Expand schema UTC for Payment Orders, Attempts, Settlements, Credit Ledger, and Payout Requests

-- 1. Add shadow TIMESTAMPTZ columns to payment_orders
ALTER TABLE payment_orders
    ADD COLUMN IF NOT EXISTS expires_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS paid_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS failed_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS credit_finalized_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS released_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS refunded_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMPTZ;

-- 2. Add shadow TIMESTAMPTZ columns to payment_attempts
ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMPTZ;

-- 3. Add shadow TIMESTAMPTZ columns to settlement_entries
ALTER TABLE settlement_entries
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ;

-- 4. Add shadow TIMESTAMPTZ columns to settlement_accounts
ALTER TABLE settlement_accounts
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMPTZ;

-- 5. Add shadow TIMESTAMPTZ columns to credit_ledger_entries
ALTER TABLE credit_ledger_entries
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ;

-- 6. Add shadow TIMESTAMPTZ columns to credit_ledger_accounts
ALTER TABLE credit_ledger_accounts
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMPTZ;

-- 7. Add shadow TIMESTAMPTZ columns to payout_requests
ALTER TABLE payout_requests
    ADD COLUMN IF NOT EXISTS requested_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reviewed_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS paid_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejected_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMPTZ;

-- 8. Backfill from legacy HCM timezone (Asia/Ho_Chi_Minh)
UPDATE payment_orders
SET
    expires_at_utc = CASE WHEN expires_at IS NOT NULL THEN expires_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    paid_at_utc = CASE WHEN paid_at IS NOT NULL THEN paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    cancelled_at_utc = CASE WHEN cancelled_at IS NOT NULL THEN cancelled_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    failed_at_utc = CASE WHEN failed_at IS NOT NULL THEN failed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    credit_finalized_at_utc = CASE WHEN credit_finalized_at IS NOT NULL THEN credit_finalized_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    released_at_utc = CASE WHEN released_at IS NOT NULL THEN released_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    refunded_at_utc = CASE WHEN refunded_at IS NOT NULL THEN refunded_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    updated_at_utc = CASE WHEN updated_at IS NOT NULL THEN updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END
WHERE created_at_utc IS NULL;

UPDATE payment_attempts
SET
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    updated_at_utc = CASE WHEN updated_at IS NOT NULL THEN updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END
WHERE created_at_utc IS NULL;

UPDATE settlement_entries
SET
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END
WHERE created_at_utc IS NULL;

UPDATE settlement_accounts
SET
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    updated_at_utc = CASE WHEN updated_at IS NOT NULL THEN updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END
WHERE created_at_utc IS NULL;

UPDATE credit_ledger_entries
SET
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END
WHERE created_at_utc IS NULL;

UPDATE credit_ledger_accounts
SET
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    updated_at_utc = CASE WHEN updated_at IS NOT NULL THEN updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END
WHERE created_at_utc IS NULL;

UPDATE payout_requests
SET
    requested_at_utc = CASE WHEN requested_at IS NOT NULL THEN requested_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    reviewed_at_utc = CASE WHEN reviewed_at IS NOT NULL THEN reviewed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    approved_at_utc = CASE WHEN approved_at IS NOT NULL THEN approved_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    paid_at_utc = CASE WHEN paid_at IS NOT NULL THEN paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    rejected_at_utc = CASE WHEN rejected_at IS NOT NULL THEN rejected_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    updated_at_utc = CASE WHEN updated_at IS NOT NULL THEN updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END
WHERE created_at_utc IS NULL;

-- 9. Bi-directional sync triggers for PostgreSQL

-- Trigger for payment_orders
CREATE OR REPLACE FUNCTION trg_sync_payment_orders_utc() RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR NEW.expires_at IS DISTINCT FROM OLD.expires_at) AND (NEW.expires_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.expires_at_utc = OLD.expires_at_utc)) THEN
        NEW.expires_at_utc := CASE WHEN NEW.expires_at IS NOT NULL THEN NEW.expires_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.expires_at_utc IS DISTINCT FROM OLD.expires_at_utc) AND NEW.expires_at = OLD.expires_at THEN
        NEW.expires_at := CASE WHEN NEW.expires_at_utc IS NOT NULL THEN (NEW.expires_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.paid_at IS DISTINCT FROM OLD.paid_at) AND (NEW.paid_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.paid_at_utc = OLD.paid_at_utc)) THEN
        NEW.paid_at_utc := CASE WHEN NEW.paid_at IS NOT NULL THEN NEW.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.paid_at_utc IS DISTINCT FROM OLD.paid_at_utc) AND NEW.paid_at = OLD.paid_at THEN
        NEW.paid_at := CASE WHEN NEW.paid_at_utc IS NOT NULL THEN (NEW.paid_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.cancelled_at IS DISTINCT FROM OLD.cancelled_at) AND (NEW.cancelled_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.cancelled_at_utc = OLD.cancelled_at_utc)) THEN
        NEW.cancelled_at_utc := CASE WHEN NEW.cancelled_at IS NOT NULL THEN NEW.cancelled_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.cancelled_at_utc IS DISTINCT FROM OLD.cancelled_at_utc) AND NEW.cancelled_at = OLD.cancelled_at THEN
        NEW.cancelled_at := CASE WHEN NEW.cancelled_at_utc IS NOT NULL THEN (NEW.cancelled_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.failed_at IS DISTINCT FROM OLD.failed_at) AND (NEW.failed_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.failed_at_utc = OLD.failed_at_utc)) THEN
        NEW.failed_at_utc := CASE WHEN NEW.failed_at IS NOT NULL THEN NEW.failed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.failed_at_utc IS DISTINCT FROM OLD.failed_at_utc) AND NEW.failed_at = OLD.failed_at THEN
        NEW.failed_at := CASE WHEN NEW.failed_at_utc IS NOT NULL THEN (NEW.failed_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.credit_finalized_at IS DISTINCT FROM OLD.credit_finalized_at) AND (NEW.credit_finalized_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.credit_finalized_at_utc = OLD.credit_finalized_at_utc)) THEN
        NEW.credit_finalized_at_utc := CASE WHEN NEW.credit_finalized_at IS NOT NULL THEN NEW.credit_finalized_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.credit_finalized_at_utc IS DISTINCT FROM OLD.credit_finalized_at_utc) AND NEW.credit_finalized_at = OLD.credit_finalized_at THEN
        NEW.credit_finalized_at := CASE WHEN NEW.credit_finalized_at_utc IS NOT NULL THEN (NEW.credit_finalized_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.released_at IS DISTINCT FROM OLD.released_at) AND (NEW.released_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.released_at_utc = OLD.released_at_utc)) THEN
        NEW.released_at_utc := CASE WHEN NEW.released_at IS NOT NULL THEN NEW.released_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.released_at_utc IS DISTINCT FROM OLD.released_at_utc) AND NEW.released_at = OLD.released_at THEN
        NEW.released_at := CASE WHEN NEW.released_at_utc IS NOT NULL THEN (NEW.released_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.refunded_at IS DISTINCT FROM OLD.refunded_at) AND (NEW.refunded_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.refunded_at_utc = OLD.refunded_at_utc)) THEN
        NEW.refunded_at_utc := CASE WHEN NEW.refunded_at IS NOT NULL THEN NEW.refunded_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.refunded_at_utc IS DISTINCT FROM OLD.refunded_at_utc) AND NEW.refunded_at = OLD.refunded_at THEN
        NEW.refunded_at := CASE WHEN NEW.refunded_at_utc IS NOT NULL THEN (NEW.refunded_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.created_at IS DISTINCT FROM OLD.created_at) AND (NEW.created_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.created_at_utc = OLD.created_at_utc)) THEN
        NEW.created_at_utc := CASE WHEN NEW.created_at IS NOT NULL THEN NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.created_at_utc IS DISTINCT FROM OLD.created_at_utc) AND NEW.created_at = OLD.created_at THEN
        NEW.created_at := CASE WHEN NEW.created_at_utc IS NOT NULL THEN (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.updated_at IS DISTINCT FROM OLD.updated_at) AND (NEW.updated_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.updated_at_utc = OLD.updated_at_utc)) THEN
        NEW.updated_at_utc := CASE WHEN NEW.updated_at IS NOT NULL THEN NEW.updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.updated_at_utc IS DISTINCT FROM OLD.updated_at_utc) AND NEW.updated_at = OLD.updated_at THEN
        NEW.updated_at := CASE WHEN NEW.updated_at_utc IS NOT NULL THEN (NEW.updated_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_payment_orders_sync_utc ON payment_orders;
CREATE TRIGGER trg_payment_orders_sync_utc
    BEFORE INSERT OR UPDATE ON payment_orders
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_payment_orders_utc();

-- Trigger for payment_attempts
CREATE OR REPLACE FUNCTION trg_sync_payment_attempts_utc() RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR NEW.created_at IS DISTINCT FROM OLD.created_at) AND (NEW.created_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.created_at_utc = OLD.created_at_utc)) THEN
        NEW.created_at_utc := CASE WHEN NEW.created_at IS NOT NULL THEN NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.created_at_utc IS DISTINCT FROM OLD.created_at_utc) AND NEW.created_at = OLD.created_at THEN
        NEW.created_at := CASE WHEN NEW.created_at_utc IS NOT NULL THEN (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.updated_at IS DISTINCT FROM OLD.updated_at) AND (NEW.updated_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.updated_at_utc = OLD.updated_at_utc)) THEN
        NEW.updated_at_utc := CASE WHEN NEW.updated_at IS NOT NULL THEN NEW.updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.updated_at_utc IS DISTINCT FROM OLD.updated_at_utc) AND NEW.updated_at = OLD.updated_at THEN
        NEW.updated_at := CASE WHEN NEW.updated_at_utc IS NOT NULL THEN (NEW.updated_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_payment_attempts_sync_utc ON payment_attempts;
CREATE TRIGGER trg_payment_attempts_sync_utc
    BEFORE INSERT OR UPDATE ON payment_attempts
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_payment_attempts_utc();

-- Trigger for settlement_entries
CREATE OR REPLACE FUNCTION trg_sync_settlement_entries_utc() RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR NEW.created_at IS DISTINCT FROM OLD.created_at) AND (NEW.created_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.created_at_utc = OLD.created_at_utc)) THEN
        NEW.created_at_utc := CASE WHEN NEW.created_at IS NOT NULL THEN NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.created_at_utc IS DISTINCT FROM OLD.created_at_utc) AND NEW.created_at = OLD.created_at THEN
        NEW.created_at := CASE WHEN NEW.created_at_utc IS NOT NULL THEN (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_settlement_entries_sync_utc ON settlement_entries;
CREATE TRIGGER trg_settlement_entries_sync_utc
    BEFORE INSERT OR UPDATE ON settlement_entries
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_settlement_entries_utc();

-- Trigger for credit_ledger_entries
CREATE OR REPLACE FUNCTION trg_sync_credit_ledger_entries_utc() RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR NEW.created_at IS DISTINCT FROM OLD.created_at) AND (NEW.created_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.created_at_utc = OLD.created_at_utc)) THEN
        NEW.created_at_utc := CASE WHEN NEW.created_at IS NOT NULL THEN NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.created_at_utc IS DISTINCT FROM OLD.created_at_utc) AND NEW.created_at = OLD.created_at THEN
        NEW.created_at := CASE WHEN NEW.created_at_utc IS NOT NULL THEN (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_credit_ledger_entries_sync_utc ON credit_ledger_entries;
CREATE TRIGGER trg_credit_ledger_entries_sync_utc
    BEFORE INSERT OR UPDATE ON credit_ledger_entries
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_credit_ledger_entries_utc();

-- Trigger for payout_requests
CREATE OR REPLACE FUNCTION trg_sync_payout_requests_utc() RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'INSERT' OR NEW.requested_at IS DISTINCT FROM OLD.requested_at) AND (NEW.requested_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.requested_at_utc = OLD.requested_at_utc)) THEN
        NEW.requested_at_utc := CASE WHEN NEW.requested_at IS NOT NULL THEN NEW.requested_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.requested_at_utc IS DISTINCT FROM OLD.requested_at_utc) AND NEW.requested_at = OLD.requested_at THEN
        NEW.requested_at := CASE WHEN NEW.requested_at_utc IS NOT NULL THEN (NEW.requested_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.reviewed_at IS DISTINCT FROM OLD.reviewed_at) AND (NEW.reviewed_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.reviewed_at_utc = OLD.reviewed_at_utc)) THEN
        NEW.reviewed_at_utc := CASE WHEN NEW.reviewed_at IS NOT NULL THEN NEW.reviewed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.reviewed_at_utc IS DISTINCT FROM OLD.reviewed_at_utc) AND NEW.reviewed_at = OLD.reviewed_at THEN
        NEW.reviewed_at := CASE WHEN NEW.reviewed_at_utc IS NOT NULL THEN (NEW.reviewed_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.approved_at IS DISTINCT FROM OLD.approved_at) AND (NEW.approved_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.approved_at_utc = OLD.approved_at_utc)) THEN
        NEW.approved_at_utc := CASE WHEN NEW.approved_at IS NOT NULL THEN NEW.approved_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.approved_at_utc IS DISTINCT FROM OLD.approved_at_utc) AND NEW.approved_at = OLD.approved_at THEN
        NEW.approved_at := CASE WHEN NEW.approved_at_utc IS NOT NULL THEN (NEW.approved_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.paid_at IS DISTINCT FROM OLD.paid_at) AND (NEW.paid_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.paid_at_utc = OLD.paid_at_utc)) THEN
        NEW.paid_at_utc := CASE WHEN NEW.paid_at IS NOT NULL THEN NEW.paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.paid_at_utc IS DISTINCT FROM OLD.paid_at_utc) AND NEW.paid_at = OLD.paid_at THEN
        NEW.paid_at := CASE WHEN NEW.paid_at_utc IS NOT NULL THEN (NEW.paid_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.rejected_at IS DISTINCT FROM OLD.rejected_at) AND (NEW.rejected_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.rejected_at_utc = OLD.rejected_at_utc)) THEN
        NEW.rejected_at_utc := CASE WHEN NEW.rejected_at IS NOT NULL THEN NEW.rejected_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.rejected_at_utc IS DISTINCT FROM OLD.rejected_at_utc) AND NEW.rejected_at = OLD.rejected_at THEN
        NEW.rejected_at := CASE WHEN NEW.rejected_at_utc IS NOT NULL THEN (NEW.rejected_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.created_at IS DISTINCT FROM OLD.created_at) AND (NEW.created_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.created_at_utc = OLD.created_at_utc)) THEN
        NEW.created_at_utc := CASE WHEN NEW.created_at IS NOT NULL THEN NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.created_at_utc IS DISTINCT FROM OLD.created_at_utc) AND NEW.created_at = OLD.created_at THEN
        NEW.created_at := CASE WHEN NEW.created_at_utc IS NOT NULL THEN (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    IF (TG_OP = 'INSERT' OR NEW.updated_at IS DISTINCT FROM OLD.updated_at) AND (NEW.updated_at_utc IS NULL OR (TG_OP = 'UPDATE' AND NEW.updated_at_utc = OLD.updated_at_utc)) THEN
        NEW.updated_at_utc := CASE WHEN NEW.updated_at IS NOT NULL THEN NEW.updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF (TG_OP = 'UPDATE' AND NEW.updated_at_utc IS DISTINCT FROM OLD.updated_at_utc) AND NEW.updated_at = OLD.updated_at THEN
        NEW.updated_at := CASE WHEN NEW.updated_at_utc IS NOT NULL THEN (NEW.updated_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp ELSE NULL END;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_payout_requests_sync_utc ON payout_requests;
CREATE TRIGGER trg_payout_requests_sync_utc
    BEFORE INSERT OR UPDATE ON payout_requests
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_payout_requests_utc();

-- 10. Indexes on shadow UTC columns
CREATE INDEX IF NOT EXISTS idx_payment_orders_expires_at_utc
    ON payment_orders (expires_at_utc);

CREATE INDEX IF NOT EXISTS idx_payment_orders_paid_at_utc
    ON payment_orders (paid_at_utc);

CREATE INDEX IF NOT EXISTS idx_payment_orders_status_updated_utc
    ON payment_orders (status, updated_at_utc);

CREATE INDEX IF NOT EXISTS idx_payment_attempts_created_at_utc
    ON payment_attempts (created_at_utc);

CREATE INDEX IF NOT EXISTS idx_settlement_entries_created_at_utc
    ON settlement_entries (created_at_utc);

CREATE INDEX IF NOT EXISTS idx_credit_ledger_entries_created_at_utc
    ON credit_ledger_entries (created_at_utc);

CREATE INDEX IF NOT EXISTS idx_payout_requests_requested_at_utc
    ON payout_requests (requested_at_utc);
