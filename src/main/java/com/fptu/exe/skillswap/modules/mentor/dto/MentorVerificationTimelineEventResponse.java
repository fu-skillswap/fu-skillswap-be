package com.fptu.exe.skillswap.modules.mentor.dto;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationEventType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record MentorVerificationTimelineEventResponse(
        UUID id,
        MentorVerificationEventType eventType,
        VerificationStatus fromStatus,
        VerificationStatus toStatus,
        UUID actorUserId,
        String actorEmail,
        String actorFullName,
        String note,
        LocalDateTime createdAt
) {
}
