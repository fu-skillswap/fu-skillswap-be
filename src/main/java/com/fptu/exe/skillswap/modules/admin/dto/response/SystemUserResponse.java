package com.fptu.exe.skillswap.modules.admin.dto.response;

import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin tài khoản người dùng trong danh sách quản trị hệ thống")
public record SystemUserResponse(
        @Schema(description = "ID của user")
        UUID userId,
        @Schema(description = "Email đăng nhập")
        String email,
        @Schema(description = "Họ tên hiển thị")
        String fullName,
        @Schema(description = "Ảnh đại diện")
        String avatarUrl,
        @Schema(description = "Trạng thái tài khoản")
        UserStatus status,
        @Schema(description = "Danh sách vai trò hiện có của user")
        List<RoleCode> roles,
        @Schema(description = "Thời điểm đăng nhập gần nhất")
        LocalDateTime lastLoginAt,
        @Schema(description = "Thời điểm tạo tài khoản")
        LocalDateTime createdAt,
        @Schema(description = "Hồ sơ học thuật của user")
        AdminUserAcademicResponse academicProfile
) {
}
