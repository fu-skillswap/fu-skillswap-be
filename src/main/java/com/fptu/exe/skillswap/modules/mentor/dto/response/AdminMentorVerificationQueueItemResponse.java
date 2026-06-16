package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record AdminMentorVerificationQueueItemResponse(
        UUID requestId,
        UUID mentorUserId,
        String mentorEmail,
        String mentorFullName,
        String mentorAvatarUrl,
        VerificationStatus status,
        Integer revisionCount,
        LocalDateTime submittedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
