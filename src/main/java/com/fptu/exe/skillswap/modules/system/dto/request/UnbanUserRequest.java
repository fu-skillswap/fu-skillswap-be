package com.fptu.exe.skillswap.modules.system.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reason payload for restoring a banned user account from admin moderation screens.")
public class UnbanUserRequest {
    @Schema(description = "Human-readable admin reason for unbanning the user", example = "Đã hoàn tất kiểm tra và cho phép người dùng hoạt động trở lại.")
    @NotBlank(message = "Lý do mở khóa tài khoản không được để trống")
    private String reason;
}
