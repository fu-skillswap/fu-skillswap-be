package com.fptu.exe.skillswap.modules.chat.dto.response;

import com.fptu.exe.skillswap.modules.chat.domain.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import com.fptu.exe.skillswap.modules.chat.domain.MessageState;

@Builder
@Schema(description = "Một tin nhắn trong cuộc trò chuyện. API danh sách hiện trả tin nhắn mới nhất trước; FE dùng sequence để đồng bộ khi realtime hoặc reconnect.")
public record MessageResponse(
        @Schema(description = "Message ID", example = "019f7234-aaaa-bbbb-cccc-1234567890ab")
        UUID id,
        @Schema(description = "Sequence tăng dần trong cuộc trò chuyện; dùng để sắp xếp tin nhắn và đồng bộ realtime/reconnect.", example = "129")
        Long sequence,
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
        @Schema(description = "Trạng thái hiện tại của tin nhắn, ví dụ đang hoạt động hoặc đã xóa.", example = "ACTIVE")
        MessageState state,
        @Schema(description = "Optimistic-lock version. Send this value as expectedVersion when editing or deleting a text message.", example = "1")
        Integer version,
        @Schema(description = "Edit time as a UTC instant", example = "2026-06-24T04:50:00Z", nullable = true)
        Instant editedAt,
        @Schema(description = "Deletion time as a UTC instant", example = "2026-06-24T04:55:00Z", nullable = true)
        Instant deletedAt,
        @Schema(description = "Participant còn lại đã đọc tin nhắn hay chưa; có thể null với tin nhắn không phải do tài khoản hiện tại gửi.", nullable = true, example = "true")
        Boolean isReadByOther,
        List<ChatAttachmentResponse> attachments,
        @Schema(description = "Message creation time as a UTC instant", example = "2026-06-24T04:45:00Z")
        Instant createdAt,
        @Schema(description = "True when this message was sent by the current user", example = "true")
        boolean isMine
) {
}
