package com.fptu.exe.skillswap.modules.system.dto.response;

import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin tài khoản đang có quyền ADMIN")
public record AdminUserResponse(
        UUID userId,
        String email,
        String fullName,
        String avatarUrl,
        UserStatus status,
        UUID assignedBy,
        LocalDateTime assignedAt
) {
}
