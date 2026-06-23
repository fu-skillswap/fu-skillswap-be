package com.fptu.exe.skillswap.modules.conversation.dto.response;

import com.fptu.exe.skillswap.modules.conversation.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ConversationResponse(
        UUID id,
        ConversationSourceType sourceType,
        UUID sourceId,
        ConversationType type,
        ConversationStatus status,
        UUID otherUserId,
        String otherUserName,
        String otherUserAvatarUrl,
        String lastMessageContent,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt
) {
}
