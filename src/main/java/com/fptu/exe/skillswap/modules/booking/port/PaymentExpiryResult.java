package com.fptu.exe.skillswap.modules.booking.port;

import java.time.Instant;
import java.util.UUID;

public record PaymentExpiryResult(
        UUID bookingId,
        String previousStatus,
        String currentStatus,
        Instant expiredAtUtc
) {
}
