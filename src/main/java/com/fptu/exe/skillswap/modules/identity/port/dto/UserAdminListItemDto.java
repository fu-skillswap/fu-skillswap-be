package com.fptu.exe.skillswap.modules.identity.port.dto;

import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record UserAdminListItemDto(
        UUID userId,
        String email,
        String fullName,
        String avatarUrl,
        UserStatus status,
        List<RoleCode> roles,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        UserAdminAcademicDto academicProfile
) {}
