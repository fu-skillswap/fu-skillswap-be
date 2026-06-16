package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record MentorDiscoveryCardResponse(
        UUID mentorUserId,
        String displayName,
        String avatarUrl,
        String headline,
        String expertiseDescription,
        String supportingSubjects,
        Boolean isAvailable,
        BigDecimal ratingAverage,
        Integer reviewCount,
        Integer completedSessions,
        TeachingMode teachingMode,
        LocalDateTime verifiedAt,
        UUID campusId,
        String campusName,
        UUID programId,
        String programName,
        UUID specializationId,
        String specializationName,
        List<MentorTagResponse> helpTopicTags
) {
}
