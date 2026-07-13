package com.fptu.exe.skillswap.modules.booking.domain;

public enum BookingCompletionOutcome {
    USER_CONFIRMED,
    AUTO_CLOSED,
    UNDER_REVIEW,
    NO_SHOW_MENTEE,
    NO_SHOW_MENTOR,
    /** Legacy values remain readable during the migration window. */
    COMPLETED_CONFIRMED,
    COMPLETED_AUTO_CLOSED,
    REVIEW_PENDING_DECISION
}
