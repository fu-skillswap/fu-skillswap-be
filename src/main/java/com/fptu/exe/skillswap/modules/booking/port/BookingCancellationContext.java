package com.fptu.exe.skillswap.modules.booking.port;

import java.time.Instant;
import java.util.UUID;

/** Immutable cancellation facts exposed to external payment workflows. */
public record BookingCancellationContext(
        UUID bookingId,
        UUID payerUserId,
        UUID mentorUserId,
        String bookingStatus,
        String cancellationReason,
        Instant acceptedAtUtc,
        Instant selectedStartAtUtc,
        Instant cancelledAtUtc,
        boolean lateCancellation,
        boolean compensationEligible
) {
}
