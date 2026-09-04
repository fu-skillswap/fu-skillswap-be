package com.fptu.exe.skillswap.modules.chat.dto.response;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
@Schema(description = "Kết quả đồng bộ trạng thái đã đọc của cuộc trò chuyện.")
public record ConversationReadResponse(
        @Schema(description = "ID cuộc trò chuyện.", example = "019f5234-aaaa-bbbb-cccc-1234567890ab") UUID conversationId,
        @Schema(description = "Sequence lớn nhất người dùng hiện tại đã đọc.", example = "128") long myLastReadSequence,
        @Schema(description = "Sequence lớn nhất participant còn lại đã đọc.", example = "130") long otherLastReadSequence,
        @Schema(description = "Số tin nhắn chưa đọc của người dùng hiện tại.", example = "3") long unreadCount) {}
