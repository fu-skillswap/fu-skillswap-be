package com.fptu.exe.skillswap.modules.chat.dto.event;

import com.fptu.exe.skillswap.modules.chat.domain.MessageType;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Sự kiện realtime gửi cho FE khi có tin nhắn mới. FE dùng messageId/sequence để chống hiển thị trùng và sắp xếp đúng thứ tự.")
public record ChatMessageEvent(
        @Schema(description = "ID cuộc trò chuyện.")
        UUID conversationId,
        @Schema(description = "ID tin nhắn ổn định để FE deduplicate khi reconnect hoặc retry.")
        UUID messageId,
        @Schema(description = "Sequence dùng để sắp xếp tin nhắn và đồng bộ khi reconnect.", example = "129")
        long sequence,
        @Schema(description = "ID người gửi; backend lấy từ tài khoản tạo tin nhắn.")
        UUID senderId,
        String senderName,
        MessageType messageType,
        String content,
        Instant createdAt,
        ConversationType conversationType,
        Boolean isSelf,
        @Schema(description = "Số tin chưa đọc của người nhận tại thời điểm phát event; chỉ áp dụng cho realtime.", nullable = true)
        Long unreadCount
) {
}
