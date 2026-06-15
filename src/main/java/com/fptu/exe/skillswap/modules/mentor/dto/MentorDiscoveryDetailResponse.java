package com.fptu.exe.skillswap.modules.mentor.dto;

import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record MentorDiscoveryDetailResponse(
        UUID mentorUserId,
        String displayName,
        String avatarUrl,
        String headline,
        String bio,
        String expertiseSummary,
        String currentPosition,
        String currentCompany,
        String industry,
        Boolean isAvailable,
        BigDecimal ratingAverage,
        Integer reviewCount,
        Integer completedSessions,
        BigDecimal hourlyRate,
        BigDecimal yearsOfExperience,
        TeachingMode teachingMode,
        Integer defaultSessionDuration,
        LocalDateTime verifiedAt,
        UUID campusId,
        String campusName,
        UUID programId,
        String programName,
        UUID specializationId,
        String specializationName,
        Integer semester,
        Boolean alumni,
        String mentoringStyle,
        String targetMentees,
        String portfolioUrl,
        String linkedinUrl,
        String githubUrl,
        List<MentorTagResponse> expertiseTags,
        List<MentorTagResponse> helpTopicTags,
        List<MentorPublicServiceResponse> services
) {
}
