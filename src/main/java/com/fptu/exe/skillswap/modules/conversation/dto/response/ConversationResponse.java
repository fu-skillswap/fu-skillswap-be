package com.fptu.exe.skillswap.modules.conversation.dto.response;

import com.fptu.exe.skillswap.modules.conversation.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;
import com.fptu.exe.skillswap.modules.conversation.domain.ChatMessagingAccess;
import com.fptu.exe.skillswap.modules.conversation.domain.ChatReadOnlyReason;

@Builder
@Schema(description = "Conversation summary shown in the current user's inbox. Conversations are typically created automatically after a booking is accepted.")
public record ConversationResponse(
        @Schema(description = "Conversation ID", example = "019f5234-aaaa-bbbb-cccc-1234567890ab")
        UUID id,
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
        long unreadCount,
        @Schema(description = "Sequence lớn nhất mà user hiện tại đã đọc", example = "128")
        long myLastReadSequence,
        @Schema(description = "Sequence lớn nhất mà participant còn lại đã đọc", example = "130")
        long otherLastReadSequence,
        ChatMessagingAccess messagingAccess,
        boolean canSendMessages,
        boolean canUploadAttachments,
        boolean canDownloadAttachments,
        ChatReadOnlyReason readOnlyReason,
        LocalDateTime messagingWindowEndsAt,
        boolean postSessionChatPermanent
) {
}
