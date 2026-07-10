package com.fptu.exe.skillswap.modules.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Thông tin đăng nhập bằng tài khoản Google")
public class GoogleLoginRequest {
    @Schema(
        description = "ID Token do Google trả về sau khi người dùng đăng nhập Google trên client. " +
            "Token này được lấy từ Google Sign-In SDK / Google Identity Services. Có thể bỏ trống nếu client dùng authorization code flow.",
        example = "eyJhbGciOiJSUzI1NiIsImtpZCI6Ij...",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String idToken;

    @Schema(
            description = "Authorization code do Google trả về nếu client dùng OAuth authorization code flow + PKCE.",
            example = "4/0AQSTgQF..."
    )
    private String authorizationCode;

    @Schema(
            description = "Redirect URI đúng với URI đã dùng để nhận authorization code.",
            example = "https://skillswap.asia/auth/google/callback"
    )
    private String redirectUri;

    @Schema(
            description = "PKCE code verifier tương ứng với authorization code flow.",
            example = "f93GhKJ0..."
    )
    private String codeVerifier;
}
