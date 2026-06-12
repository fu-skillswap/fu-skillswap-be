package com.fptu.exe.skillswap.modules.mentor.dto;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record MentorProfileResponse(
        boolean exists,
        boolean requiredFieldsCompleted,
        UUID userId,
        String email,
        String displayName,
        String avatarUrl,
        MentorStatus mentorStatus,
        String headline,
        String currentPosition,
        String currentCompany,
        Boolean isAvailable,
        LocalDateTime verifiedAt,
        String bio,
        String expertiseSummary,
        List<MentorTagResponse> expertiseTags,
        List<MentorTagResponse> helpTopics,
        BigDecimal yearsOfExperience,
        String industry,
        String linkedinUrl,
        String githubUrl,
        String portfolioUrl,
        TeachingMode teachingMode,
        Integer sessionDuration,
        BigDecimal hourlyRate,
        String mentoringStyle,
        String targetMentees,
        BigDecimal ratingAverage,
        Integer reviewCount,
        Integer completedSessions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MentorProfileResponse empty(UUID userId) {
        return MentorProfileResponse.builder()
                .exists(false)
                .requiredFieldsCompleted(false)
                .userId(userId)
                .expertiseTags(List.of())
                .helpTopics(List.of())
                .build();
    }
}
