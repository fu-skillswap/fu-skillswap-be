package com.fptu.exe.skillswap.modules.booking.port;

import java.time.OffsetDateTime;

/** Short-lived download link returned to an authorized administrator. */
public record BookingIssueEvidenceDownloadView(String downloadUrl, OffsetDateTime expiresAt) { }
