package com.fptu.exe.skillswap.modules.booking.port;

import java.time.LocalDateTime;

/** Immutable SLA result; status is the existing serialized Booking status name. */
public record BookingDisputeDeadlineView(
        LocalDateTime issueResponseDeadline,
        LocalDateTime adminResolutionDeadline,
        LocalDateTime autoReleaseDeadline,
        String disputeSlaStatus
) { }
