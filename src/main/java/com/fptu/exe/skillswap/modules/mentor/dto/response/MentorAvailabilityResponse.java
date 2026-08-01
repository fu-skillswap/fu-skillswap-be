package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Public booking-offer readiness, not viewer-specific booking authorization.")
public record MentorAvailabilityResponse(
        Boolean isAvailable,
        @Schema(nullable = true) LocalDateTime suspendedUntil,
        boolean canRequestBooking
) {
}
