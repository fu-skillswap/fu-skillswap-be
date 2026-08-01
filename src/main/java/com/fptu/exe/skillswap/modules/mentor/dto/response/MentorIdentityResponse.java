package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Identity and verification signals shown first on a public mentor profile.")
public record MentorIdentityResponse(
        UUID mentorUserId,
        String displayName,
        String avatarUrl,
        String headline,
        boolean isVerified,
        @Schema(nullable = true) LocalDateTime verifiedAt
) {
}
