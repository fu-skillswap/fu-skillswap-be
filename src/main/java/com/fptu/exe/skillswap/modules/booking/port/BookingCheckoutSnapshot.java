package com.fptu.exe.skillswap.modules.booking.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable Booking facts required to price and create a payment checkout.
 * Booking remains responsible for deriving these facts from its aggregate.
 */
public record BookingCheckoutSnapshot(
        UUID bookingId,
        UUID menteeUserId,
        UUID mentorUserId,
        UUID serviceId,
        Boolean serviceIsFree,
        Integer servicePriceScoin,
        Integer serviceDurationMinutes,
        String serviceTitle,
        String status,
        Instant paymentDeadlineUtc
) {
}
