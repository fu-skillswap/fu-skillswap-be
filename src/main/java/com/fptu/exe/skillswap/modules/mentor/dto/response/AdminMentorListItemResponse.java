package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record AdminMentorListItemResponse(
        UUID mentorUserId,
        String email,
        String displayName,
        String avatarUrl,
        UserStatus userStatus,
        MentorStatus mentorStatus,
        Boolean isAvailable,
        LocalDateTime bookingSuspendedUntil,
        String headline,
        TeachingMode teachingMode,
        Integer sessionDuration,
        BigDecimal ratingAverage,
        Integer reviewCount,
        Integer completedSessions,
        Integer rejectedBookings,
        BigDecimal lateCancellationPenaltyPoints,
        LocalDateTime verifiedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
