package com.fptu.exe.skillswap.modules.identity.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record GoogleAuthorizationContextResponse(
        @Schema(description = "Opaque one-time OAuth state. FE must return it unchanged.") String state,
        @Schema(description = "State expiration time in UTC.") Instant expiresAt
) {
}
