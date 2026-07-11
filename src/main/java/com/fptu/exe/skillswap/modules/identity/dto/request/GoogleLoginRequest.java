package com.fptu.exe.skillswap.modules.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
            description = "Authorization code do Google trả về nếu client dùng OAuth authorization code flow + PKCE.",
            example = "4/0AQSTgQF..."
    )
    @NotBlank
    private String authorizationCode;

    @Schema(
            description = "Redirect URI đúng với URI đã dùng để nhận authorization code.",
            example = "https://skillswap.asia/auth/google/callback"
    )
    @NotBlank
    private String redirectUri;

    @Schema(
            description = "PKCE code verifier tương ứng với authorization code flow.",
            example = "f93GhKJ0..."
    )
    @NotBlank
    private String codeVerifier;

    @Schema(description = "Opaque one-time state issued by GET /api/auth/google/authorization-context.")
    @NotBlank
    private String state;
}
