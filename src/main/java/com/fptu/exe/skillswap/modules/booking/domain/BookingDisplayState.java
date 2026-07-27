package com.fptu.exe.skillswap.modules.booking.domain;

/** Read-only UI grouping; persisted lifecycle/payment/settlement fields remain authoritative. */
public enum BookingDisplayState {
    PENDING_MENTOR_RESPONSE,
    PAYMENT_REQUIRED,
    MENTOR_ACTION_REQUIRED,
    UPCOMING,
    IN_SESSION,
    WAITING_CONFIRMATION,
    UNDER_REVIEW,
    FEEDBACK_REQUIRED,
    COMPLETED,
    CANCELED_OR_EXPIRED
}
