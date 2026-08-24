-- Historical status and outcome variants are normalized to one lifecycle vocabulary.
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_completion_outcome_check;

UPDATE bookings
SET completion_outcome = CASE completion_outcome
    WHEN 'COMPLETED_CONFIRMED' THEN 'USER_CONFIRMED'
    WHEN 'COMPLETED_AUTO_CLOSED' THEN 'AUTO_CLOSED'
    WHEN 'REVIEW_PENDING_DECISION' THEN 'UNDER_REVIEW'
    ELSE completion_outcome
END
WHERE completion_outcome IN ('COMPLETED_CONFIRMED', 'COMPLETED_AUTO_CLOSED', 'REVIEW_PENDING_DECISION');

-- AUTO_CLOSED and NO_SHOW were historical raw statuses. The current lifecycle
-- keeps one terminal status (COMPLETED) and records the business outcome separately.
UPDATE bookings
SET status = 'COMPLETED',
    completion_outcome = COALESCE(completion_outcome, 'AUTO_CLOSED')
WHERE status = 'AUTO_CLOSED';

UPDATE bookings
SET status = 'COMPLETED',
    completion_outcome = CASE
        WHEN issue_type = 'MENTOR_NO_SHOW' THEN 'NO_SHOW_MENTOR'
        WHEN issue_type = 'MENTEE_NO_SHOW' THEN 'NO_SHOW_MENTEE'
        ELSE COALESCE(completion_outcome, 'AUTO_CLOSED')
    END
WHERE status = 'NO_SHOW';

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
                'UNDER_REVIEW'
            )
        );

ALTER TABLE bookings
    ADD CONSTRAINT bookings_completion_outcome_check
        CHECK (completion_outcome IS NULL OR completion_outcome IN (
            'USER_CONFIRMED',
            'AUTO_CLOSED',
            'UNDER_REVIEW',
            'NO_SHOW_MENTEE',
            'NO_SHOW_MENTOR'
        ));
