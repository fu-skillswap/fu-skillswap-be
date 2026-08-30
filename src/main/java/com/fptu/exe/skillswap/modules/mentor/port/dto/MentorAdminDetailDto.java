package com.fptu.exe.skillswap.modules.mentor.port.dto;

import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record MentorAdminDetailDto(
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
        List<MentorSubjectResultResponse> subjectResults,
        Integer foundationSupportLevel,
        Integer outputReviewSupportLevel,
        Integer directionSupportLevel,
        List<MentorFeaturedProjectResponse> featuredProjects,
        List<MentorAchievementResponse> achievements,
        String supportingSubjects,
        TeachingMode teachingMode,
        Integer sessionDuration,
        BigDecimal ratingAverage,
        Integer reviewCount,
        Integer completedSessions,
        Integer rejectedBookings,
        String portfolioUrl,
        String linkedinUrl,
        String githubUrl,
        String primaryLabel,
        LocalDateTime verifiedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
