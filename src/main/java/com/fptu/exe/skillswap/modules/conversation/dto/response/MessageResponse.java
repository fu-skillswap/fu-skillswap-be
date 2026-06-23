package com.fptu.exe.skillswap.modules.conversation.dto.response;

import com.fptu.exe.skillswap.modules.conversation.domain.MessageType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String senderName,
        MessageType messageType,
        String content,
        LocalDateTime createdAt,
        boolean isMine
) {
}
