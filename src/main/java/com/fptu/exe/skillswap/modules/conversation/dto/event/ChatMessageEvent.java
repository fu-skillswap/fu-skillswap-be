package com.fptu.exe.skillswap.modules.conversation.dto.event;

import com.fptu.exe.skillswap.modules.conversation.domain.MessageType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ChatMessageEvent(
        UUID conversationId,
        UUID messageId,
        UUID senderId,
        String senderName,
        MessageType messageType,
        String content,
        LocalDateTime createdAt
) {
}
