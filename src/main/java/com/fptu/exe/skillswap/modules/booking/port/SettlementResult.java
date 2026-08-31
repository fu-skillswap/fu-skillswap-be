package com.fptu.exe.skillswap.modules.booking.port;

import java.time.Instant;
import java.util.UUID;

public record SettlementResult(
        UUID bookingId,
        UUID mentorUserId,
        Integer amountScoin,
        String settlementStatus,
        Instant completedAtUtc
) {
}
