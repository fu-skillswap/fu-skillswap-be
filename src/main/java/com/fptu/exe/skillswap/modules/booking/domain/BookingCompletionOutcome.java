package com.fptu.exe.skillswap.modules.booking.domain;

public enum BookingCompletionOutcome {
    USER_CONFIRMED,
    AUTO_CLOSED,
    UNDER_REVIEW,
    NO_SHOW_MENTEE,
    NO_SHOW_MENTOR,
    /** Admin closed a non-no-show dispute with an explicit split of the held escrow. */
    PARTIALLY_SETTLED,
    /** Fallback only after an escalated dispute exceeded the published admin SLA. */
    ADMIN_SLA_AUTO_RELEASED
}
