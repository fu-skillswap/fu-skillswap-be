package com.fptu.exe.skillswap.modules.booking.port;

import java.time.Instant;
import java.util.UUID;

/** Immutable Booking data required by Payment settlement workflows. */
public record BookingSettlementSnapshot(
        UUID bookingId,
        UUID payerUserId,
        UUID mentorUserId,
        Integer amountScoin,
        String bookingStatus,
        String completionOutcome,
        BookingIssueResolutionSnapshot issueResolution,
        boolean settlementEligible,
        Instant selectedStartAtUtc,
        Instant paymentDeadlineUtc
) {
}
