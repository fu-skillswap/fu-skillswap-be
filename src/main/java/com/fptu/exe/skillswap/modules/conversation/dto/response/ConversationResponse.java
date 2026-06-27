package com.fptu.exe.skillswap.modules.conversation.dto.response;

import com.fptu.exe.skillswap.modules.conversation.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Conversation summary shown in the current user's inbox. Conversations are typically created automatically after a booking is accepted.")
public record ConversationResponse(
        @Schema(description = "Conversation ID", example = "019f5234-aaaa-bbbb-cccc-1234567890ab")
        UUID id,
        @Schema(description = "Business source that created the conversation", example = "BOOKING")
        ConversationSourceType sourceType,
        @Schema(description = "ID of the source entity, such as the booking that created this conversation", example = "019f4234-aaaa-bbbb-cccc-1234567890ab")
        UUID sourceId,
        @Schema(description = "Conversation type", example = "DIRECT")
        ConversationType type,
        @Schema(description = "Conversation status", example = "ACTIVE")
        ConversationStatus status,
        @Schema(description = "The other participant user ID from the current user's perspective", example = "019f6234-aaaa-bbbb-cccc-1234567890ab")
        UUID otherUserId,
        @Schema(description = "Display name of the other participant", example = "Nguyen Van B")
        String otherUserName,
        @Schema(description = "Avatar URL of the other participant", example = "https://lh3.googleusercontent.com/example")
        String otherUserAvatarUrl,
        @Schema(description = "Preview of the latest message in the conversation", example = "Anh đã cập nhật meeting link cho buổi mentoring.")
        String lastMessageContent,
        @Schema(description = "Timestamp of the latest message", example = "2026-06-24T11:45:00")
        LocalDateTime lastMessageAt,
        @Schema(description = "Conversation creation time", example = "2026-06-24T10:30:00")
        LocalDateTime createdAt,
        @Schema(description = "Số lượng tin nhắn chưa đọc đối với user hiện tại", example = "3")
        long unreadCount
) {
}
