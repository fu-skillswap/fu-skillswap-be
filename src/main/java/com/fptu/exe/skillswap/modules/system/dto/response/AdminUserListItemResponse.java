package com.fptu.exe.skillswap.modules.system.dto.response;

import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin user thường trong danh sách quản trị dành cho admin")
public record AdminUserListItemResponse(
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
        @Schema(description = "Danh sách vai trò hiện có, chỉ gồm MENTEE hoặc MENTOR")
        List<RoleCode> roles,
        @Schema(description = "Thời điểm đăng nhập gần nhất")
        LocalDateTime lastLoginAt,
        @Schema(description = "Thời điểm tạo tài khoản")
        LocalDateTime createdAt
) {
}
