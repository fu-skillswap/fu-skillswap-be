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
            description = "Google ID Token nằm trong trường credential do GIS trả về.",
            example = "eyJhbGciOiJSUzI1NiIsImtpZCI6..."
    )
    @NotBlank
    private String credential;

    @Schema(
            description = "Nonce một lần do GET /api/auth/google/nonce cấp và đã truyền vào GIS.",
            example = "pBt_T5mF..."
    )
    @NotBlank
    private String nonce;
}
