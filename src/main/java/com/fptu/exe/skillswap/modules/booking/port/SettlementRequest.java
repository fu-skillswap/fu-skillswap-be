package com.fptu.exe.skillswap.modules.booking.port;

import java.time.Instant;
import java.util.UUID;

/** Immutable settlement input shared across the Booking-Payment boundary. */
public record SettlementRequest(
        UUID bookingId,
        String settlementType,
        Integer amountScoin,
        BookingIssueResolutionSnapshot resolution,
        String context,
        Instant requestedAtUtc
) {
}
