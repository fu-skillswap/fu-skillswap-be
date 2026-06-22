package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

        @Schema(description = "List of subjects/topics the mentor can support", example = "PRJ301, SWE302, SWP391")
        String supportingSubjects,

        @Schema(description = "Mentoring mode: ONLINE, OFFLINE, HYBRID", example = "ONLINE")
        TeachingMode teachingMode,

        @Schema(description = "Standard session duration in minutes", example = "60")
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

        @Schema(description = "Mentor's LinkedIn profile URL", example = "https://linkedin.com/in/mentor-demo", nullable = true)
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
