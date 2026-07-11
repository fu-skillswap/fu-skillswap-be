ALTER TABLE credit_ledger_accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE settlement_accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE mentor_payout_profiles ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
