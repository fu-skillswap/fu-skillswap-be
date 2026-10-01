package com.fptu.exe.skillswap.modules.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DataDeletionPublicRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        @Schema(description = "Địa chỉ email của tài khoản cần xóa", example = "user@fpt.edu.vn")
        String email,

        @Size(max = 1000, message = "Lý do tối đa 1000 ký tự")
        @Schema(description = "Lý do hoặc ghi chú bổ sung khi yêu cầu xóa tài khoản", example = "Tôi không còn nhu cầu sử dụng dịch vụ")
        String reason
) {
}
