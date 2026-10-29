package com.fptu.exe.skillswap.modules.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu gửi lại mã OTP xác thực")
public record EmailResendOtpRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Schema(description = "Địa chỉ email nhận mã xác thực", example = "student@fpt.edu.vn")
        String email
) {
}
