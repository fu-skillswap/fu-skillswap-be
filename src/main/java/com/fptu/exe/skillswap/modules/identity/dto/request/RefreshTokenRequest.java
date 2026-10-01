package com.fptu.exe.skillswap.modules.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request làm mới token dành cho client di động")
public record RefreshTokenRequest(
        @Schema(description = "Refresh token dùng cho client di động (Flutter). Web browser để trống và dùng HttpOnly cookie.", example = "eyJhbGciOiJIUzI1NiIsIn...")
        String refreshToken
) {}
