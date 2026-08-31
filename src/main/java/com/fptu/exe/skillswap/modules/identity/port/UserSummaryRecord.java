package com.fptu.exe.skillswap.modules.identity.port;

import com.fptu.exe.skillswap.shared.constant.RoleCode;

import java.util.Set;
import java.util.UUID;

public record UserSummaryRecord(
        UUID userId,
        String email,
        String fullName,
        String avatarUrl,
        Set<RoleCode> roles,
        String status,
        boolean isEmailVerified
) {
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean hasRole(RoleCode role) {
        return roles != null && roles.contains(role);
    }
}
