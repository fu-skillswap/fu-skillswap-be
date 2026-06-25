ALTER TABLE payment_orders
    ADD COLUMN IF NOT EXISTS provider_payment_link_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS provider_status VARCHAR(40);

ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS provider_payment_link_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS provider_status VARCHAR(40);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_attempts_provider_order_code
    ON payment_attempts(provider_order_code)
    WHERE provider_order_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_attempts_provider_payment_link_id
    ON payment_attempts(provider_payment_link_id);
