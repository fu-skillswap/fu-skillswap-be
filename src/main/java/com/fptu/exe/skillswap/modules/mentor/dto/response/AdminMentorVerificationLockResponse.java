package com.fptu.exe.skillswap.modules.mentor.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record AdminMentorVerificationLockResponse(
        UUID requestId,
        boolean locked,
        boolean canReview,
        UUID lockedByAdminId,
        String lockedByAdminEmail,
        String lockedByAdminFullName,
        LocalDateTime lockedAt,
        LocalDateTime lockExpiresAt,
        long secondsRemaining
) {
}
