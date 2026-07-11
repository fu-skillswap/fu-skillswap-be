package com.fptu.exe.skillswap.modules.conversation.dto.response;

import com.fptu.exe.skillswap.modules.conversation.domain.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Message item returned from a conversation thread. Messages are currently listed in newest-first order by the conversation APIs.")
public record MessageResponse(
        @Schema(description = "Message ID", example = "019f7234-aaaa-bbbb-cccc-1234567890ab")
        UUID id,
        @Schema(description = "Conversation ID that owns the message", example = "019f5234-aaaa-bbbb-cccc-1234567890ab")
        UUID conversationId,
        @Schema(description = "Sender user ID. This can be null for system-generated messages.", example = "019f6234-aaaa-bbbb-cccc-1234567890ab")
        UUID senderId,
        @Schema(description = "Display name of the sender", example = "Nguyen Van B")
        String senderName,
        @Schema(description = "Message type", example = "TEXT")
        MessageType messageType,
        @Schema(description = "Message content", example = "Chào em, anh đã cập nhật meeting link cho buổi mentoring.")
        String content,
        @Schema(description = "Message creation time", example = "2026-06-24T11:45:00")
        LocalDateTime createdAt,
        @Schema(description = "True when this message was sent by the current user", example = "true")
        boolean isMine
) {
}
