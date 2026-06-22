package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record AdminMentorDetailResponse(
        UUID mentorUserId,
        String email,
        String displayName,
        String avatarUrl,
        String phoneNumber,
        UserStatus userStatus,
        MentorStatus mentorStatus,
        Boolean isAvailable,
        LocalDateTime bookingSuspendedUntil,
        String headline,
        String expertiseDescription,
        String supportingSubjects,
        TeachingMode teachingMode,
        Integer sessionDuration,
        BigDecimal ratingAverage,
        Integer reviewCount,
        Integer completedSessions,
        Integer rejectedBookings,
        BigDecimal lateCancellationPenaltyPoints,
        String portfolioUrl,
        String linkedinUrl,
        String githubUrl,
        String primaryLabel,
        LocalDateTime verifiedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
