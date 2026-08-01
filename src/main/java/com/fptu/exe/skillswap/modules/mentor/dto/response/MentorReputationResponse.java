package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Review and completed-session signals. completedSessions counts only USER_CONFIRMED bookings.")
public record MentorReputationResponse(
        @Schema(example = "RATED") MentorRatingState ratingState,
        @Schema(nullable = true, example = "4.85") BigDecimal ratingAverage,
        @Schema(example = "27") Integer reviewCount,
        @Schema(example = "18") Integer completedSessions
) {
}
