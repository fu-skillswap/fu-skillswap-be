package com.fptu.exe.skillswap.modules.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Yêu cầu xác thực mã OTP kích hoạt tài khoản hoặc liên kết tài khoản")
public record EmailVerifyRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Schema(description = "Địa chỉ email cần xác thực", example = "student@fpt.edu.vn")
        String email,

        @NotBlank(message = "Mã xác thực không được để trống")
        @Pattern(regexp = "^\\d{6}$", message = "Mã xác thực phải gồm đúng 6 chữ số")
        @Schema(description = "Mã xác thực 6 chữ số nhận qua email", example = "123456")
        String otpCode
) {
}
