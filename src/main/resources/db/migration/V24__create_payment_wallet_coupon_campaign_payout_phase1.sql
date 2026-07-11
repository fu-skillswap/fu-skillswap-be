CREATE TABLE IF NOT EXISTS payment_orders (
    id UUID NOT NULL,
    order_code VARCHAR(80) NOT NULL,
    booking_id UUID NOT NULL,
    payer_user_id UUID NOT NULL,
    mentor_user_id UUID NOT NULL,
    service_id UUID,
    gross_scoin INTEGER NOT NULL DEFAULT 0,
    coupon_id UUID,
    coupon_code_snapshot VARCHAR(100),
    coupon_discount_scoin INTEGER NOT NULL DEFAULT 0,
    campaign_credit_scoin INTEGER NOT NULL DEFAULT 0,
    user_credit_scoin INTEGER NOT NULL DEFAULT 0,
    remaining_payable_scoin INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    payment_provider VARCHAR(30) NOT NULL,
    provider_order_code VARCHAR(100),
    provider_transaction_id VARCHAR(100),
    payment_link TEXT,
    expires_at TIMESTAMP(6),
    paid_at TIMESTAMP(6),
    cancelled_at TIMESTAMP(6),
    failed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_payment_orders_order_code UNIQUE (order_code),
    CONSTRAINT uq_payment_orders_booking_id UNIQUE (booking_id),
    CONSTRAINT uq_payment_orders_provider_order_code UNIQUE (provider_order_code)
);

CREATE TABLE IF NOT EXISTS payment_attempts (
    id UUID NOT NULL,
    payment_order_id UUID NOT NULL,
    attempt_no INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_order_code VARCHAR(100),
    provider_transaction_id VARCHAR(100),
    checkout_url TEXT,
    failure_reason TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_attempts_order FOREIGN KEY (payment_order_id) REFERENCES payment_orders(id),
    CONSTRAINT uq_payment_attempts_order_attempt UNIQUE (payment_order_id, attempt_no),
    CONSTRAINT uq_payment_attempts_provider_txn UNIQUE (provider_transaction_id)
);

CREATE INDEX IF NOT EXISTS idx_payment_attempts_order_id ON payment_attempts(payment_order_id);
CREATE INDEX IF NOT EXISTS idx_payment_attempts_status ON payment_attempts(status);

CREATE TABLE IF NOT EXISTS credit_ledger_accounts (
    id UUID NOT NULL,
    owner_type VARCHAR(40) NOT NULL,
    owner_id UUID NOT NULL,
    account_code VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_credit_ledger_accounts_owner UNIQUE (owner_type, owner_id),
    CONSTRAINT uq_credit_ledger_accounts_code UNIQUE (account_code)
);

CREATE TABLE IF NOT EXISTS credit_ledger_entries (
    id UUID NOT NULL,
    account_id UUID NOT NULL,
    entry_type VARCHAR(30) NOT NULL,
    origin_type VARCHAR(40) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID,
    amount_scoin INTEGER NOT NULL,
    balance_effect_scoin INTEGER NOT NULL,
    memo TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_credit_ledger_entries_account FOREIGN KEY (account_id) REFERENCES credit_ledger_accounts(id)
);

CREATE INDEX IF NOT EXISTS idx_credit_entries_account ON credit_ledger_entries(account_id);
CREATE INDEX IF NOT EXISTS idx_credit_entries_source ON credit_ledger_entries(source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_credit_entries_origin ON credit_ledger_entries(origin_type);

CREATE TABLE IF NOT EXISTS settlement_accounts (
    id UUID NOT NULL,
    owner_type VARCHAR(40) NOT NULL,
    owner_id UUID NOT NULL,
    account_code VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_settlement_accounts_owner UNIQUE (owner_type, owner_id),
    CONSTRAINT uq_settlement_accounts_code UNIQUE (account_code)
);

CREATE TABLE IF NOT EXISTS settlement_entries (
    id UUID NOT NULL,
    account_id UUID NOT NULL,
    entry_type VARCHAR(30) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID,
    amount_scoin INTEGER NOT NULL,
    balance_effect_scoin INTEGER NOT NULL,
    commission_scoin INTEGER,
    memo TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_settlement_entries_account FOREIGN KEY (account_id) REFERENCES settlement_accounts(id)
);

CREATE INDEX IF NOT EXISTS idx_settlement_entries_account ON settlement_entries(account_id);
CREATE INDEX IF NOT EXISTS idx_settlement_entries_source ON settlement_entries(source_type, source_id);

CREATE TABLE IF NOT EXISTS coupons (
    id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT,
    discount_type VARCHAR(20) NOT NULL,
    discount_value INTEGER NOT NULL,
    max_discount_scoin INTEGER,
    status VARCHAR(20) NOT NULL,
    start_at TIMESTAMP(6),
    end_at TIMESTAMP(6),
    quota_total INTEGER,
    quota_per_user INTEGER,
    min_order_value_scoin INTEGER,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_coupons_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_coupons_status ON coupons(status);
CREATE INDEX IF NOT EXISTS idx_coupons_time_window ON coupons(start_at, end_at);

CREATE TABLE IF NOT EXISTS coupon_applicable_service_ids (
    coupon_id UUID NOT NULL,
    service_id UUID NOT NULL,
    CONSTRAINT fk_coupon_applicable_services_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id)
);

CREATE TABLE IF NOT EXISTS coupon_applicable_mentor_ids (
    coupon_id UUID NOT NULL,
    mentor_user_id UUID NOT NULL,
    CONSTRAINT fk_coupon_applicable_mentors_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id)
);

CREATE TABLE IF NOT EXISTS coupon_applicable_help_topic_ids (
    coupon_id UUID NOT NULL,
    help_topic_id UUID NOT NULL,
    CONSTRAINT fk_coupon_applicable_help_topics_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id)
);

CREATE TABLE IF NOT EXISTS coupon_redemptions (
    id UUID NOT NULL,
    coupon_id UUID NOT NULL,
    payment_order_id UUID NOT NULL,
    redeemer_user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    discount_scoin INTEGER NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_coupon_redemptions_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id),
    CONSTRAINT fk_coupon_redemptions_order FOREIGN KEY (payment_order_id) REFERENCES payment_orders(id),
    CONSTRAINT uq_coupon_redemptions_order UNIQUE (payment_order_id)
);

CREATE INDEX IF NOT EXISTS idx_coupon_redemptions_coupon_id ON coupon_redemptions(coupon_id);
CREATE INDEX IF NOT EXISTS idx_coupon_redemptions_user_id ON coupon_redemptions(redeemer_user_id);

CREATE TABLE IF NOT EXISTS campaigns (
    id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    funding_source VARCHAR(30) NOT NULL,
    start_at TIMESTAMP(6),
    end_at TIMESTAMP(6),
    budget_scoin INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_campaigns_status ON campaigns(status);
CREATE INDEX IF NOT EXISTS idx_campaigns_time_window ON campaigns(start_at, end_at);

CREATE TABLE IF NOT EXISTS campaign_audience_role_codes (
    campaign_id UUID NOT NULL,
    role_code VARCHAR(40) NOT NULL,
    CONSTRAINT fk_campaign_audience_roles_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id)
);

CREATE TABLE IF NOT EXISTS campaign_audience_campus_ids (
    campaign_id UUID NOT NULL,
    campus_id UUID NOT NULL,
    CONSTRAINT fk_campaign_audience_campuses_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id)
);

CREATE TABLE IF NOT EXISTS campaign_audience_program_ids (
    campaign_id UUID NOT NULL,
    program_id UUID NOT NULL,
    CONSTRAINT fk_campaign_audience_programs_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id)
);

CREATE TABLE IF NOT EXISTS campaign_audience_specialization_ids (
    campaign_id UUID NOT NULL,
    specialization_id UUID NOT NULL,
    CONSTRAINT fk_campaign_audience_specializations_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id)
);

CREATE TABLE IF NOT EXISTS campaign_audience_help_topic_ids (
    campaign_id UUID NOT NULL,
    help_topic_id UUID NOT NULL,
    CONSTRAINT fk_campaign_audience_help_topics_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id)
);

CREATE TABLE IF NOT EXISTS campaign_benefits (
    id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    benefit_type VARCHAR(30) NOT NULL,
    credit_scoin INTEGER,
    coupon_code VARCHAR(80),
    coupon_discount_type VARCHAR(20),
    coupon_discount_value INTEGER,
    coupon_max_discount_scoin INTEGER,
    coupon_quota_total INTEGER,
    coupon_quota_per_user INTEGER,
    coupon_min_order_value_scoin INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_campaign_benefits_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id)
);

CREATE TABLE IF NOT EXISTS payout_requests (
    id UUID NOT NULL,
    mentor_user_id UUID NOT NULL,
    settlement_account_id UUID NOT NULL,
    amount_scoin INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    admin_user_id UUID,
    admin_note TEXT,
    requested_at TIMESTAMP(6) NOT NULL,
    reviewed_at TIMESTAMP(6),
    approved_at TIMESTAMP(6),
    paid_at TIMESTAMP(6),
    rejected_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payout_requests_settlement FOREIGN KEY (settlement_account_id) REFERENCES settlement_accounts(id)
);

CREATE INDEX IF NOT EXISTS idx_payout_requests_mentor_id ON payout_requests(mentor_user_id);
CREATE INDEX IF NOT EXISTS idx_payout_requests_status ON payout_requests(status);
