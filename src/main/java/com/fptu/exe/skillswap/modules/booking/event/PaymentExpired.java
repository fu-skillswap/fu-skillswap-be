package com.fptu.exe.skillswap.modules.booking.event;

import java.time.Instant;
import java.util.UUID;

/** Published when the Booking payment window expires. */
public record PaymentExpired(
        UUID bookingId,
        Instant expiredAtUtc
) {
}
