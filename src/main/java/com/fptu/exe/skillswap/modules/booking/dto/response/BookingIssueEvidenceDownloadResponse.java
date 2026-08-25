package com.fptu.exe.skillswap.modules.booking.dto.response;

import java.time.OffsetDateTime;

public record BookingIssueEvidenceDownloadResponse(String downloadUrl, OffsetDateTime expiresAt) {
}
