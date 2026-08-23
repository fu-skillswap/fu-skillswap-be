-- rollout: EXPAND
-- ACCEPTED predates the payment-aware booking lifecycle. New bookings use
-- ACCEPTED_AWAITING_PAYMENT until payment succeeds, or PAID when they are
-- confirmed without payment. Preserve legacy confirmed bookings as PAID.
UPDATE bookings
SET status = 'PAID'
WHERE status = 'ACCEPTED';

DROP INDEX IF EXISTS uq_bookings_slot_accepted;
DROP INDEX IF EXISTS uq_bookings_mentee_slot_active;

-- A mentee may retain only one pending request for a parent slot.
CREATE UNIQUE INDEX IF NOT EXISTS uq_bookings_mentee_slot_pending
    ON bookings (mentee_user_id, slot_id)
    WHERE slot_id IS NOT NULL
      AND status = 'PENDING';

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check;

ALTER TABLE bookings
    ADD CONSTRAINT bookings_status_check
        CHECK (
            status IN (
                'PENDING',
                'ACCEPTED_AWAITING_PAYMENT',
                'PAID',
                'REJECTED',
                'EXPIRED',
                'CANCELLED_BY_MENTEE',
                'CANCELLED_BY_MENTOR',
                'AWAITING_MENTOR_COMPLETION',
                'AWAITING_MENTEE_CONFIRMATION',
                'COMPLETED',
                'AUTO_CLOSED',
                'UNDER_REVIEW',
                'NO_SHOW'
            )
        );
