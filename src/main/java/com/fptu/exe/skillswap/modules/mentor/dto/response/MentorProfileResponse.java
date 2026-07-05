package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Mentor profile response used in mentor onboarding, settings, and mentor readiness checks.")
public record MentorProfileResponse(
        @Schema(description = "False when the current user has not created a mentor profile yet", example = "true")
        boolean exists,
        @Schema(description = "True when the mentor profile has enough required fields to satisfy backend completeness checks", example = "true")
        boolean requiredFieldsCompleted,
        @Schema(description = "Current user ID", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
        UUID userId,
        @Schema(description = "Current user email", example = "mentor@fpt.edu.vn")
        String email,
        @Schema(description = "Display name shown to mentees", example = "Nguyen Van B")
        String displayName,
        @Schema(description = "Avatar URL used in mentor screens", example = "https://lh3.googleusercontent.com/example")
        String avatarUrl,
        @Schema(description = "Mentor lifecycle status", example = "ACTIVE")
        MentorStatus mentorStatus,
        @Schema(description = "Short mentor headline shown in profile and discovery", example = "Backend Developer | Spring Boot Mentor")
        String headline,
        @Schema(description = "Main expertise description shown to mentees", example = "Mình có kinh nghiệm xây dựng REST API với Spring Boot, PostgreSQL và triển khai Docker.")
        String expertiseDescription,
        @Schema(description = "Whether the mentor is currently available for new mentee bookings", example = "true")
        Boolean isAvailable,
        @Schema(description = "Temporary booking suspension end time if the mentor is under booking penalty", nullable = true, example = "2026-06-27T10:00:00")
        LocalDateTime bookingSuspendedUntil,
        @Schema(description = "Late cancellation penalty points tracked by the backend", example = "0.50")
        BigDecimal lateCancellationPenaltyPoints,
        @Schema(description = "Timestamp when the mentor was verified by admin", nullable = true, example = "2026-06-20T14:30:00")
        LocalDateTime verifiedAt,
        @Schema(description = "Selected help topics that describe what the mentor can support")
        List<MentorTagResponse> helpTopics,
        @Schema(description = "Môn - điểm mentor dùng cho peer matching")
        List<MentorSubjectResultResponse> subjectResults,
        @Schema(description = "Mức mentor có thể giúp mentee lấy gốc, 1-4", example = "3")
        Integer foundationSupportLevel,
        @Schema(description = "Mức mentor có thể review bài nộp/project/CV/report, 1-4", example = "3")
        Integer outputReviewSupportLevel,
        @Schema(description = "Mức mentor có thể hỗ trợ định hướng/OJT/career, 1-4", example = "2")
        Integer directionSupportLevel,
        @Schema(description = "Dự án tiêu biểu optional của mentor")
        List<MentorFeaturedProjectResponse> featuredProjects,
        @Schema(description = "Học vấn và giải thưởng optional của mentor")
        List<MentorAchievementResponse> achievements,
        @Schema(description = "GitHub URL", nullable = true, example = "https://github.com/example")
        String githubUrl,
        @Schema(description = "Portfolio URL", nullable = true, example = "https://example.dev")
        String portfolioUrl,
        @Schema(example = "0912345678")
        String phoneNumber,
        @Schema(description = "Average mentor rating shown in mentor-facing and discovery surfaces", example = "4.80")
        BigDecimal ratingAverage,
        @Schema(description = "Total number of public or stored reviews for this mentor", example = "12")
        Integer reviewCount,
        @Schema(description = "Total completed mentoring sessions counted for this mentor", example = "18")
        Integer completedSessions,
        @Schema(description = "Profile creation time", example = "2026-06-10T08:00:00")
        LocalDateTime createdAt,
        @Schema(description = "Latest profile update time", example = "2026-06-24T10:30:00")
        LocalDateTime updatedAt
) {

    public static MentorProfileResponse empty(UUID userId) {
        return MentorProfileResponse.builder()
                .exists(false)
                .requiredFieldsCompleted(false)
                .userId(userId)
                .isAvailable(true)
                .lateCancellationPenaltyPoints(BigDecimal.ZERO)
                .helpTopics(List.of())
                .subjectResults(List.of())
                .featuredProjects(List.of())
                .achievements(List.of())
                .build();
    }
}
