package com.fptu.exe.skillswap.modules.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Thông tin đăng nhập bằng email và mật khẩu")
public record EmailLoginRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Schema(description = "Địa chỉ email đăng nhập", example = "student@fpt.edu.vn")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Schema(description = "Mật khẩu đăng nhập", example = "SkillSwap@2026")
        String password
) {
}
