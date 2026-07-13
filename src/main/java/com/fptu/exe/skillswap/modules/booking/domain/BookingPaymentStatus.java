package com.fptu.exe.skillswap.modules.booking.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Canonical public payment lifecycle status used by booking APIs.")
public enum BookingPaymentStatus {
    NOT_REQUIRED,
    PENDING,
    PAID,
    FAILED,
    EXPIRED,
    REFUNDED
}
