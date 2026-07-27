package com.fptu.exe.skillswap.modules.blog.dto;

import com.fptu.exe.skillswap.modules.blog.domain.BlogAuthorType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Blog author summary enriched from SkillSwap user identity.")
public record BlogAuthorResponse(
        UUID id,
        String displayName,
        String avatarUrl,
        BlogAuthorType authorType
) {
}
