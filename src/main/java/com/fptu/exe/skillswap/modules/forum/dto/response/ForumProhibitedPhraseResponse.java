package com.fptu.exe.skillswap.modules.forum.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ForumProhibitedPhraseResponse(
        UUID ruleId,
        String phrase,
        boolean isActive,
        Integer version,
        UUID createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
