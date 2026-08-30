package com.fptu.exe.skillswap.modules.identity.port.dto;

import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record UserAdminDto(
        UUID userId,
        String email,
        String fullName,
        String avatarUrl,
        UserStatus status,
        UUID assignedBy,
        LocalDateTime assignedAt
) {}
