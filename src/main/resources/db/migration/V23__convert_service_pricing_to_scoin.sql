-- Convert MentorService pricing from VND-like amount to internal SCoin.
-- Rule: 1 SCoin = 1,000 VND. Legacy values that are not divisible by 1,000
-- are rounded down to the nearest whole SCoin to keep the migration deterministic.

ALTER TABLE mentor_services
    ADD COLUMN IF NOT EXISTS price_scoin INTEGER NOT NULL DEFAULT 0;

UPDATE mentor_services
SET price_scoin = GREATEST(
        0,
        CAST(FLOOR(COALESCE(price_amount, 0) / 1000.0) AS INTEGER)
    );

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS service_price_scoin_snapshot INTEGER;

UPDATE bookings
SET service_price_scoin_snapshot = CASE
    WHEN service_price_amount_snapshot IS NULL THEN NULL
    ELSE GREATEST(0, CAST(FLOOR(service_price_amount_snapshot / 1000.0) AS INTEGER))
END;
