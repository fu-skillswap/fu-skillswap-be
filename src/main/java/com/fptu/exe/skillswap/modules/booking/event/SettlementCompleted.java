package com.fptu.exe.skillswap.modules.booking.event;

import java.time.Instant;
import java.util.UUID;

/** Published when settlement for a booking has completed. */
public record SettlementCompleted(
        UUID bookingId,
        UUID mentorUserId,
        Integer amountScoin,
        Instant completedAtUtc
) {
}
