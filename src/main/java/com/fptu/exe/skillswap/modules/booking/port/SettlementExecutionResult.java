package com.fptu.exe.skillswap.modules.booking.port;

import java.time.Instant;
import java.util.UUID;

/** Immutable outcome of a settlement/compensation execution. */
public record SettlementExecutionResult(
        UUID executionId,
        UUID bookingId,
        boolean successful,
        String resultStatus,
        Integer payerCompensationScoin,
        Integer mentorSettlementScoin,
        Integer platformAmountScoin,
        String failureReason,
        Instant executedAtUtc
) {
}
