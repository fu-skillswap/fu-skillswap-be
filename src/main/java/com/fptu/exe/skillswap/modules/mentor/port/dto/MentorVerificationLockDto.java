package com.fptu.exe.skillswap.modules.mentor.port.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record MentorVerificationLockDto(
        UUID requestId,
        boolean locked,
        boolean canReview,
        UUID lockedByAdminId,
        String lockedByAdminEmail,
        String lockedByAdminFullName,
        LocalDateTime lockedAt,
        LocalDateTime lockExpiresAt,
        long secondsRemaining
) {}
