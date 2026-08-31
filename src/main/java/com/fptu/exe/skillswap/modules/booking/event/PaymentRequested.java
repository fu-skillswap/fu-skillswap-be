package com.fptu.exe.skillswap.modules.booking.event;

import java.time.Instant;
import java.util.UUID;

/** Published when a payment attempt is requested for a booking. */
public record PaymentRequested(
        UUID bookingId,
        UUID payerUserId,
        Integer amountScoin,
        Instant requestedAtUtc
) {
}
