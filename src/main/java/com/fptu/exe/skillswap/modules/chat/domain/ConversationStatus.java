package com.fptu.exe.skillswap.modules.chat.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trạng thái cuộc trò chuyện. ACTIVE: hoạt động bình thường; LOCKED: bị khóa, FE giữ lịch sử để đọc nếu response cho phép nhưng tắt gửi/upload. Không tự mở khóa từ phía client.")
public enum ConversationStatus {
    ACTIVE,
    LOCKED
}
