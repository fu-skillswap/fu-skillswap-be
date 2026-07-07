package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Builder
@Schema(description = "Detailed profile response for admin mentor management")
public record AdminMentorDetailResponse(
        @Schema(description = "Mentor's unique user ID", example = "019eee11-8715-7078-9f12-52f4fdb2a411")
        UUID mentorUserId,

        @Schema(description = "Mentor's email address", example = "mentor.demo@skillswap.local")
        String email,

        @Schema(description = "Mentor's display name", example = "Quang Tam")
        String displayName,

        @Schema(description = "Mentor's avatar image URL", example = "https://lh3.googleusercontent.com/...")
        String avatarUrl,

        @Schema(description = "Mentor's phone number", example = "+84987654321", nullable = true)
        String phoneNumber,

        @Schema(description = "General user account status", example = "ACTIVE")
        UserStatus userStatus,

        @Schema(description = "Mentor profile status: DRAFT, PENDING_VERIFICATION, ACTIVE, REJECTED, SUSPENDED", example = "ACTIVE")
        MentorStatus mentorStatus,

        @Schema(description = "Flag indicating if the mentor is available for bookings", example = "true")
        Boolean isAvailable,

        @Schema(description = "Suspension end timestamp, if bookings are currently suspended", example = "2026-06-25T12:00:00", nullable = true)
        LocalDateTime bookingSuspendedUntil,

        @Schema(description = "Mentor's short professional headline", example = "Backend Developer, DevOps, Cloud Architect")
        String headline,

        @Schema(description = "Detailed description of expertise and mentoring goals", example = "Mentoring students in building production-ready REST APIs using Java and deploying to AWS.")
        String expertiseDescription,

        @Schema(description = "Danh sách môn - điểm phục vụ peer mentoring")
        List<MentorSubjectResultResponse> subjectResults,

        @Schema(description = "Mức mentor có thể giúp mentee lấy gốc, 1-4", example = "3")
        Integer foundationSupportLevel,

        @Schema(description = "Mức mentor có thể review bài nộp/project/CV/report, 1-4", example = "3")
        Integer outputReviewSupportLevel,

        @Schema(description = "Mức mentor có thể hỗ trợ định hướng/OJT/career, 1-4", example = "2")
        Integer directionSupportLevel,

        @Schema(description = "Các help topic mentor đang chọn")
        List<MentorTagResponse> helpTopics,

        @Schema(description = "Dự án tiêu biểu optional của mentor")
        List<MentorFeaturedProjectResponse> featuredProjects,

        @Schema(description = "Học vấn/giải thưởng optional của mentor")
        List<MentorAchievementResponse> achievements,

        @Schema(description = "Legacy summary field, không còn là source of truth. FE mới dùng subjectResults.", example = "PRJ301, SWE302, SWP391", deprecated = true, hidden = true)
        String supportingSubjects,

        @Schema(description = "Legacy field cũ, FE mới không nên dùng.", example = "ONLINE", deprecated = true, hidden = true)
        TeachingMode teachingMode,

        @Schema(description = "Legacy field cũ, FE mới không nên dùng.", example = "60", deprecated = true, hidden = true)
        Integer sessionDuration,

        @Schema(description = "Average rating score from reviews", example = "4.8")
        BigDecimal ratingAverage,

        @Schema(description = "Total number of reviews received", example = "10")
        Integer reviewCount,

        @Schema(description = "Number of completed mentoring sessions", example = "15")
        Integer completedSessions,

        @Schema(description = "Number of rejected bookings", example = "1")
        Integer rejectedBookings,

        @Schema(description = "Late cancellation penalty points accumulated", example = "0.5")
        BigDecimal lateCancellationPenaltyPoints,

        @Schema(description = "Mentor's portfolio site URL", example = "https://myportfolio.local", nullable = true)
        String portfolioUrl,

        @Schema(description = "Legacy field cũ, FE mới không nên dùng.", example = "https://linkedin.com/in/mentor-demo", nullable = true, deprecated = true, hidden = true)
        String linkedinUrl,

        @Schema(description = "Mentor's GitHub profile URL", example = "https://github.com/mentor-demo", nullable = true)
        String githubUrl,

        @Schema(description = "Primary label badge, usually maps to AcademicProgram code", example = "CNTT", nullable = true)
        String primaryLabel,

        @Schema(description = "Timestamp when the mentor profile was verified", example = "2026-06-22T21:20:25", nullable = true)
        LocalDateTime verifiedAt,

        @Schema(description = "Timestamp when the mentor profile was first created", example = "2026-06-19T10:00:00")
        LocalDateTime createdAt,

        @Schema(description = "Timestamp when the mentor profile was last updated", example = "2026-06-22T21:20:25")
        LocalDateTime updatedAt
) {
}
