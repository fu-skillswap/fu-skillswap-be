package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Card gọn cho Discovery. Card dùng cùng ngôn ngữ section với profile detail,
 * nhưng chỉ mang dữ liệu cần để người dùng quyết định có mở profile hay không.
 */
@Builder
@Schema(description = "Thông tin mentor gọn cho trang Khám phá. Các phần được nhóm giống profile detail, không trả evidence đầy đủ.")
public record MentorDiscoveryCardResponse(
        Identity identity,
        Mentoring mentoring,
        Evidence evidence,
        Reputation reputation,
        Availability availability,
        Match match,
        @JsonIgnore UUID mentorUserId,
        @JsonIgnore String displayName,
        @JsonIgnore MentorRatingState ratingState,
        @JsonIgnore BigDecimal ratingAverage,
        @JsonIgnore Integer completedSessions,
        @JsonIgnore BigDecimal matchScore
) {

    public record Identity(UUID mentorUserId, String displayName, String avatarUrl, String headline,
                           boolean isVerified, LocalDateTime verifiedAt) {
    }

    public record Mentoring(String expertiseDescription, Integer foundationSupportLevel,
                            Integer outputReviewSupportLevel, Integer directionSupportLevel) {
    }

    public record Evidence(UUID campusId, String campusName, UUID programId, String programName,
                           UUID specializationId, String specializationName,
                           List<MentorSubjectResultResponse> subjectHighlights,
                           List<MentorFeaturedProjectResponse> featuredProjects,
                           List<MentorAchievementResponse> achievements) {
        public Evidence {
            subjectHighlights = subjectHighlights == null ? List.of() : List.copyOf(subjectHighlights);
            featuredProjects = featuredProjects == null ? List.of() : List.copyOf(featuredProjects);
            achievements = achievements == null ? List.of() : List.copyOf(achievements);
        }
    }

    public record Reputation(MentorRatingState ratingState, BigDecimal ratingAverage,
                             Integer reviewCount, Integer completedSessions) {
    }

    public record Availability(boolean isAvailable) {
    }

    public record Match(BigDecimal score) {
    }

}
