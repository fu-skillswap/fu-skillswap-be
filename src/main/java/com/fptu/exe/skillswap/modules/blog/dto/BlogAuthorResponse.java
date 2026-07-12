package com.fptu.exe.skillswap.modules.blog.dto;

import com.fptu.exe.skillswap.shared.constant.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;
import java.util.UUID;

@Schema(description = "Blog author summary enriched from SkillSwap user identity.")
public record BlogAuthorResponse(
        UUID userId,
        String fullName,
        String avatarUrl,
        Set<RoleCode> roles,
        boolean mentor
) {
}
