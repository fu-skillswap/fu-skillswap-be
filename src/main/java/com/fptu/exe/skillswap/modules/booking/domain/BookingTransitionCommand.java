package com.fptu.exe.skillswap.modules.booking.domain;

/** Commands that are allowed to move a booking through its persisted lifecycle. */
public enum BookingTransitionCommand {
    ACCEPT_FREE,
    ACCEPT_PAID,
    REJECT,
    SYSTEM_REJECT,
    EXPIRE_PENDING,
    EXPIRE_PAYMENT,
    CANCEL_BY_MENTEE,
    CANCEL_BY_MENTOR,
    PAYMENT_CONFIRMED,
    SESSION_ENDED,
    MENTOR_COMPLETED,
    MENTEE_CONFIRMED,
    ISSUE_REPORTED,
    AUTO_CLOSE,
    AUTO_RESOLVE_MENTOR_NO_SHOW,
    AUTO_RESOLVE_MENTEE_NO_SHOW,
    AUTO_RELEASE_AFTER_ADMIN_SLA,
    ADMIN_CONFIRM_SESSION,
    ADMIN_CONFIRM_MENTOR_NO_SHOW,
    ADMIN_CONFIRM_MENTEE_NO_SHOW,
    /** Reverses a prior dispute decision and returns the booking to UNDER_REVIEW. */
    ADMIN_REVERSE_RESOLUTION
}
