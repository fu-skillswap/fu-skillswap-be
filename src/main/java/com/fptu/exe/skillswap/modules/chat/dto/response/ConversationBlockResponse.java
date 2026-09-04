package com.fptu.exe.skillswap.modules.chat.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ConversationBlockResponse(
        UUID conversationId,
        UUID blockerUserId,
        UUID blockedUserId,
        boolean blocked,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Block creation time as a UTC instant", example = "2026-06-24T04:45:00Z", nullable = true)
        Instant createdAt
) {
}
