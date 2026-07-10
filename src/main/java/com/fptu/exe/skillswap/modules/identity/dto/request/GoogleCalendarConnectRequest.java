package com.fptu.exe.skillswap.modules.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Thông tin authorization code flow để kết nối Google Calendar của mentor")
public record GoogleCalendarConnectRequest(
        @NotBlank(message = "authorizationCode không được để trống")
        @Schema(description = "Authorization code do Google trả về", example = "4/0AQSTgQF...")
        String authorizationCode,

        @NotBlank(message = "redirectUri không được để trống")
        @Schema(description = "Redirect URI đã dùng khi lấy authorization code", example = "https://skillswap.asia/google-calendar/callback")
        String redirectUri,

        @NotBlank(message = "codeVerifier không được để trống")
        @Schema(description = "PKCE code verifier", example = "f93GhKJ0...")
        String codeVerifier
) {
}
