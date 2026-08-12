package com.fptu.exe.skillswap.modules.chat.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationBlockResponse(
        UUID conversationId,
        UUID blockerUserId,
        UUID blockedUserId,
        boolean blocked,
        LocalDateTime createdAt
) {
}
