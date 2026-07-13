package com.fptu.exe.skillswap.modules.booking.domain;

/** Business and financial transitions only; this is not an activity log. */
public enum BookingEventType {
    BOOKING_CREATED,
    MENTOR_ACCEPTED,
    PAYMENT_CONFIRMED,
    POST_SESSION_STARTED,
    MENTOR_COMPLETED,
    MENTEE_CONFIRMED,
    ISSUE_CREATED,
    ISSUE_RESPONDED,
    ISSUE_RESOLVED,
    MENTOR_COMPLETION_OVERDUE,
    AUTO_CLOSED,
    SETTLEMENT_RELEASED,
    REFUND_EXECUTED
}
