package com.fptu.exe.skillswap.modules.booking.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Booking lifecycle status used across mentee, mentor, admin, notification, and review flows.")
public enum BookingStatus {
    PENDING,
    ACCEPTED_AWAITING_PAYMENT,
    ACCEPTED,
    PAID,
    REJECTED,
    EXPIRED,
    CANCELLED_BY_MENTEE,
    CANCELLED_BY_MENTOR,
    AWAITING_MENTOR_COMPLETION,
    AWAITING_MENTEE_CONFIRMATION,
    COMPLETED,
    AUTO_CLOSED,
    UNDER_REVIEW,
    NO_SHOW
}
