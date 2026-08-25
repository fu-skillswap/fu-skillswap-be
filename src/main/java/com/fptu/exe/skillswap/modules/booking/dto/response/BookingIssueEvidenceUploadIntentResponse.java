package com.fptu.exe.skillswap.modules.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "URL tải private, ngắn hạn cho một file minh chứng dispute")
public record BookingIssueEvidenceUploadIntentResponse(
        UUID uploadIntentId,
        String uploadUrl,
        OffsetDateTime expiresAt,
        String contentType
) {
}
