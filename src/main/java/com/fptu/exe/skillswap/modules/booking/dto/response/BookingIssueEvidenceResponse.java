package com.fptu.exe.skillswap.modules.booking.dto.response;

import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueEvidenceState;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueEvidenceSubmissionSide;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Metadata an toàn của một file minh chứng dispute; không bao gồm storage key hoặc URL tải")
public record BookingIssueEvidenceResponse(
        UUID evidenceId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        BookingIssueEvidenceSubmissionSide submissionSide,
        BookingIssueEvidenceState state,
        OffsetDateTime attachedAt,
        boolean canDownload
) {
}
