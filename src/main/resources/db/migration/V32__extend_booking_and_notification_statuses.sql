ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check;

ALTER TABLE bookings
    ADD CONSTRAINT bookings_status_check
        CHECK (
            status IN (
                'PENDING',
                'ACCEPTED_AWAITING_PAYMENT',
                'ACCEPTED',
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

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
        CHECK (
            type IN (
                'MENTOR_VERIFICATION_APPROVED',
                'MENTOR_VERIFICATION_REJECTED',
                'MENTOR_VERIFICATION_NEEDS_REVISION',
                'BOOKING_REQUEST_CREATED',
                'BOOKING_ACCEPTED',
                'BOOKING_PAYMENT_CONFIRMED',
                'BOOKING_PAYMENT_EXPIRED',
                'BOOKING_REJECTED',
                'BOOKING_CANCELLED_BY_MENTEE',
                'BOOKING_CANCELLED_BY_MENTOR',
                'BOOKING_AUTO_REJECTED',
                'BOOKING_REQUEST_EXPIRED',
                'BOOKING_RESCHEDULE_REQUESTED',
                'BOOKING_RESCHEDULE_ACCEPTED',
                'BOOKING_RESCHEDULE_REJECTED',
                'BOOKING_RESCHEDULE_EXPIRED',
                'MEETING_LINK_UPDATED',
                'SESSION_COMPLETED',
                'FEEDBACK_RECEIVED',
                'FORUM_POST_COMMENTED',
                'FORUM_POST_HIDDEN',
                'FORUM_COMMENT_HIDDEN',
                'ACCOUNT_UNLOCKED'
            )
        );
