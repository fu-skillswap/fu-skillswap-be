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
@Schema(description = "Google ID Token do ứng dụng Mobile cung cấp")
public class GoogleMobileLoginRequest {

    @NotBlank
    @Schema(
            description = "Google ID Token do Google Mobile SDK trả về.",
            example = "eyJhbGciOiJSUzI1NiIsImtpZCI6..."
    )
    private String credential;
}
