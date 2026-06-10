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
        description = "ID Token do Google trả về sau khi người dùng đăng nhập Google trên client. " +
            "Token này được lấy từ Google Sign-In SDK / Google Identity Services.",
        example = "eyJhbGciOiJSUzI1NiIsImtpZCI6Ij...",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "idToken không được để trống")
    private String idToken;
}
