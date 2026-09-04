package com.fptu.exe.skillswap.modules.notification.dto.event;

import io.swagger.v3.oas.annotations.media.Schema;

/** Realtime unread badge update sent to the current user's WebSocket queue. */
@Schema(description = "Sự kiện cập nhật badge notification qua WebSocket. FE dùng unreadCount để cập nhật số chưa đọc.")
public record NotificationBadgeRealtimeEvent(
        @Schema(description = "Tổng số notification chưa đọc của tài khoản hiện tại.", example = "3")
        long unreadCount,
        @Schema(description = "Loại thay đổi badge, ví dụ CREATED, READ hoặc READ_ALL.", example = "CREATED")
        String eventKind
) {
}
