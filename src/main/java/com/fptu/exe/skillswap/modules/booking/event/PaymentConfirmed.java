package com.fptu.exe.skillswap.modules.booking.event;

import java.time.Instant;
import java.util.UUID;

/** Published after Booking accepts a confirmed payment. */
public record PaymentConfirmed(
        UUID bookingId,
        UUID payerUserId,
        Integer amountScoin,
        Instant confirmedAtUtc
) {
}
