package com.fptu.exe.skillswap.modules.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request đăng xuất dành cho client di động")
public record LogoutRequest(
        @Schema(description = "Refresh token cần thu hồi cho client di động. Web browser để trống và dùng HttpOnly cookie.", example = "eyJhbGciOiJIUzI1NiIsIn...")
        String refreshToken
) {}
