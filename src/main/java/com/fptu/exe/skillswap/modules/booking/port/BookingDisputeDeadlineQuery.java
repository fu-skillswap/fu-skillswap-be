package com.fptu.exe.skillswap.modules.booking.port;

import java.time.LocalDateTime;

/** Booking-owned SLA calculation used by operational read models. */
public interface BookingDisputeDeadlineQuery {

    BookingDisputeDeadlineView resolve(LocalDateTime issueSubmittedAt, LocalDateTime adminEscalatedAt,
                                       LocalDateTime adminSlaOverdueAt, LocalDateTime issueResolvedAt);
}
