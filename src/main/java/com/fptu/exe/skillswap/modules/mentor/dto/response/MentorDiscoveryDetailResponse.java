package com.fptu.exe.skillswap.modules.mentor.dto.response;

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
        String expertiseDescription,
        String supportingSubjects,
        Boolean isAvailable,
        LocalDateTime bookingSuspendedUntil,
        BigDecimal ratingAverage,
        Integer reviewCount,
        Integer completedSessions,
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
        String portfolioUrl,
        String linkedinUrl,
        String githubUrl,
        List<MentorTagResponse> helpTopicTags,
        List<MentorServiceResponse> services
) {
}
