package com.fptu.exe.skillswap.infrastructure.security;

import com.fptu.exe.skillswap.shared.constant.RoleCode;

import java.util.List;
import java.util.UUID;

public record UserAuthSnapshot(
        UUID userId,
        String email,
        List<RoleCode> roles
) {
}
