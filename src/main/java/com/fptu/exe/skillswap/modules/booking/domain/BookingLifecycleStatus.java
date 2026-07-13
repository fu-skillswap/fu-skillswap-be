package com.fptu.exe.skillswap.modules.booking.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Canonical public booking lifecycle status used by booking, reminder, calendar and analytics APIs.")
public enum BookingLifecycleStatus {
    REQUESTED,
    WAITING_PAYMENT,
    CONFIRMED,
    REJECTED_BY_MENTOR,
    CANCELED_BY_MENTEE,
    CANCELED_BY_MENTOR,
    REQUEST_EXPIRED,
    PAYMENT_EXPIRED,
    UNDER_REVIEW,
    COMPLETED
}
