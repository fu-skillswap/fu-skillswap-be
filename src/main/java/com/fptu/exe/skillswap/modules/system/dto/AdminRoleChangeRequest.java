package com.fptu.exe.skillswap.modules.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu cấp hoặc thu hồi quyền admin theo email")
public record AdminRoleChangeRequest(
        @Schema(description = "Email tài khoản đã tồn tại trong hệ thống", example = "admin@fpt.edu.vn")
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        String email
) {
}
