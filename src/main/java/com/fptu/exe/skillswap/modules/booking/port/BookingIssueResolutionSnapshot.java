package com.fptu.exe.skillswap.modules.booking.port;

import java.time.Instant;
import java.util.UUID;

/** Immutable payment-facing data for an admin issue resolution. */
public record BookingIssueResolutionSnapshot(
        UUID resolutionId,
        String action,
        String reasonCode,
        String status,
        Integer menteeBps,
        Integer mentorBps,
        Integer platformBps,
        Integer escrowScoin,
        Integer menteeRefundScoin,
        Integer mentorSettlementScoin,
        Integer platformSettlementScoin,
        Instant settlementAppliedAtUtc,
        UUID reversalOfResolutionId
) {
}
