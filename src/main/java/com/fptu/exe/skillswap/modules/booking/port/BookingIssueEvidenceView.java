package com.fptu.exe.skillswap.modules.booking.port;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Stable, admin-facing evidence metadata owned by the Booking API. */
public record BookingIssueEvidenceView(
        UUID evidenceId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String submissionSide,
        String state,
        OffsetDateTime attachedAt,
        boolean canDownload
) { }
