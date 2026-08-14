-- rollout: EXPAND
-- Payment orders were generalized from booking-only orders to typed payment targets.
ALTER TABLE payment_orders
    ADD COLUMN IF NOT EXISTS target_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS target_id UUID;

-- booking_id is legacy-only after introducing COURSE_ENROLLMENT payment targets.
ALTER TABLE payment_orders
    ALTER COLUMN booking_id DROP NOT NULL;

-- All historical payment orders were booking orders before the typed target model.
UPDATE payment_orders
SET target_type = 'BOOKING',
    target_id = booking_id
WHERE target_type IS NULL
  AND target_id IS NULL;

ALTER TABLE payment_orders
    ALTER COLUMN target_type SET NOT NULL,
    ALTER COLUMN target_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_orders_target
    ON payment_orders(target_type, target_id);
