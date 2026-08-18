package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationUploadIntentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/** Trạng thái an toàn để FE khôi phục upload trực tiếp lên private storage. */
public record MentorVerificationUploadIntentStatusResponse(
        UUID uploadIntentId,
        MentorVerificationUploadIntentStatus status,
        LocalDateTime expiresAt,
        boolean canRetry,
        UUID confirmedDocumentId
) {
}
