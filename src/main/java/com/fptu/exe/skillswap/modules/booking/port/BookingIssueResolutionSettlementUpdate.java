package com.fptu.exe.skillswap.modules.booking.port;

import java.time.Instant;

/** Immutable settlement state written back to a Booking-owned issue resolution. */
public record BookingIssueResolutionSettlementUpdate(
        String status,
        Integer escrowScoin,
        Integer menteeRefundScoin,
        Integer mentorSettlementScoin,
        Integer platformSettlementScoin,
        Instant settlementAppliedAtUtc
) {
}
