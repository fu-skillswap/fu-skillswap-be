package com.fptu.exe.skillswap.modules.chat.dto.event;

import com.fptu.exe.skillswap.modules.chat.domain.MessageType;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ChatMessageEvent(
        UUID conversationId,
        UUID messageId,
        long sequence,
        UUID senderId,
        String senderName,
        MessageType messageType,
        String content,
        LocalDateTime createdAt,
        ConversationType conversationType,
        Boolean isSelf,
        Long unreadCount
) {
}
