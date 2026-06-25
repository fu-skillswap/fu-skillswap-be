ALTER TABLE payment_orders
    ADD COLUMN IF NOT EXISTS commission_rate_bps INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS campaign_id UUID,
    ADD COLUMN IF NOT EXISTS campaign_name_snapshot VARCHAR(150),
    ADD COLUMN IF NOT EXISTS campaign_funding_source VARCHAR(30),
    ADD COLUMN IF NOT EXISTS mentor_net_scoin INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS commission_scoin INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS provider_event_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS credit_finalized_at TIMESTAMP(6);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_orders_provider_event_id
    ON payment_orders(provider_event_id)
    WHERE provider_event_id IS NOT NULL;

UPDATE payment_orders
SET commission_rate_bps = 1000
WHERE commission_rate_bps = 0;

UPDATE payment_orders
SET commission_scoin = FLOOR((gross_scoin * commission_rate_bps) / 10000.0),
    mentor_net_scoin = GREATEST(0, gross_scoin - FLOOR((gross_scoin * commission_rate_bps) / 10000.0))
WHERE mentor_net_scoin = 0 AND gross_scoin > 0;

ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS provider_event_id VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_attempts_provider_event_id
    ON payment_attempts(provider_event_id)
    WHERE provider_event_id IS NOT NULL;

ALTER TABLE settlement_entries
    ADD COLUMN IF NOT EXISTS gross_scoin INTEGER,
    ADD COLUMN IF NOT EXISTS commission_rate_bps INTEGER,
    ADD COLUMN IF NOT EXISTS mentor_net_scoin INTEGER;

CREATE TABLE IF NOT EXISTS mentor_payout_profiles (
    id UUID NOT NULL,
    mentor_user_id UUID NOT NULL,
    account_holder_name VARCHAR(150) NOT NULL,
    bank_code VARCHAR(50),
    bank_name VARCHAR(150) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_mentor_payout_profiles_mentor
    ON mentor_payout_profiles(mentor_user_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mentor_payout_profiles_default_active
    ON mentor_payout_profiles(mentor_user_id)
    WHERE is_default = TRUE AND is_active = TRUE;

ALTER TABLE payout_requests
    ADD COLUMN IF NOT EXISTS payout_profile_id UUID,
    ADD COLUMN IF NOT EXISTS bank_account_name_snapshot VARCHAR(150),
    ADD COLUMN IF NOT EXISTS bank_name_snapshot VARCHAR(150),
    ADD COLUMN IF NOT EXISTS bank_account_number_masked_snapshot VARCHAR(30);
