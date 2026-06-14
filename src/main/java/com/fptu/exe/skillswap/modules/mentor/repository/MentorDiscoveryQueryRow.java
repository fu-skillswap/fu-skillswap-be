package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MentorDiscoveryQueryRow(
        UUID mentorUserId,
        String displayName,
        String avatarUrl,
        String headline,
        String currentPosition,
        String currentCompany,
        Boolean isAvailable,
        BigDecimal ratingAverage,
        Integer reviewCount,
        Integer completedSessions,
        BigDecimal hourlyRate,
        TeachingMode teachingMode,
        LocalDateTime verifiedAt,
        UUID campusId,
        String campusName,
        UUID programId,
        String programName,
        UUID specializationId,
        String specializationName,
        Integer semester,
        Boolean alumni
) {
}
