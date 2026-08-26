package com.fptu.exe.skillswap.modules.booking.domain;

public enum BookingCompletionOutcome {
    USER_CONFIRMED,
    AUTO_CLOSED,
    UNDER_REVIEW,
    NO_SHOW_MENTEE,
    NO_SHOW_MENTOR,
    /** Fallback only after an escalated dispute exceeded the published admin SLA. */
    ADMIN_SLA_AUTO_RELEASED
}
