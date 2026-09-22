package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Builder
@Schema(description = "Lightweight item response for the admin mentor management list table")
public record AdminMentorListItemResponse(
        @Schema(description = "Mentor's unique user ID", example = "019eee11-8715-7078-9f12-52f4fdb2a411")
        UUID mentorUserId,

        @Schema(description = "Mentor's display name", example = "Quang Tam")
        String displayName,

        @Schema(description = "Mentor's email address", example = "mentor.demo@skillswap.local")
        String email,

        @Schema(description = "Mentor's avatar image URL", example = "https://lh3.googleusercontent.com/...")
        String avatarUrl,

        @Schema(description = "Number of completed mentoring sessions", example = "15")
        Integer completedSessions,

        @Schema(description = "Average rating score from reviews", example = "4.8")
        BigDecimal ratingAverage,

        @Schema(description = "Mentor profile status: DRAFT, PENDING_VERIFICATION, ACTIVE, REJECTED, SUSPENDED", example = "ACTIVE")
        MentorStatus mentorStatus,

        @Schema(description = "Date and time when the mentor profile was first created", example = "2026-06-22T21:20:25")
        LocalDateTime createdAt
) {
}
