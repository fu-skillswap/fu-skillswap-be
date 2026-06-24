package com.fptu.exe.skillswap.modules.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Refresh token payload used when the frontend sends the token in the request body instead of relying on the HttpOnly cookie fallback.")
public class RefreshTokenRequest {
    @Schema(description = "Refresh token previously issued by SkillSwap. This field is optional only when the frontend uses the configured refresh-token cookie flow.", example = "eyJhbGciOiJIUzI1NiJ9.refresh-token-example")
    @NotBlank(message = "Mã làm mới phiên đăng nhập không được để trống")
    private String refreshToken;
}
