package com.fptu.exe.skillswap.modules.booking.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Booking lifecycle status used across mentee, mentor, admin, notification, and review flows.")
public enum BookingStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED_BY_MENTEE,
    CANCELLED_BY_MENTOR,
    COMPLETED,
    NO_SHOW
}
