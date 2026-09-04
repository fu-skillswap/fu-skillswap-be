package com.fptu.exe.skillswap.infrastructure.security;

import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;

import java.util.List;
import java.util.UUID;

public record UserAuthSnapshot(
        UUID userId,
        String email,
        List<RoleCode> roles,
        UserStatus status
) {
    public UserAuthSnapshot(UUID userId, String email, List<RoleCode> roles) {
        this(userId, email, roles, UserStatus.ACTIVE);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
