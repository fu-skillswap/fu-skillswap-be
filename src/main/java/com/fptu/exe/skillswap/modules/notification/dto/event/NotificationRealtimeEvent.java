package com.fptu.exe.skillswap.modules.notification.dto.event;

import com.fptu.exe.skillswap.modules.notification.dto.response.NotificationResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket notification item projection. Its JSON fields intentionally match
 * the historical NotificationResponse payload so existing consumers continue
 * to work while REST and realtime responsibilities are separated in code.
 */
@Schema(description = "Sự kiện notification gửi qua WebSocket. JSON giữ nguyên tên field của payload realtime hiện tại để FE không phải sửa ngay.")
public record NotificationRealtimeEvent(
        UUID notificationId,
        String type,
        String title,
        String message,
        String relatedEntityType,
        UUID relatedEntityId,
        String deepLink,
        String actionType,
        boolean read,
        Instant readAt,
        Instant createdAt,
        Long unreadCount,
        String realtimeEventKind
) {
    public static NotificationRealtimeEvent from(NotificationResponse response) {
        return new NotificationRealtimeEvent(
                response.getNotificationId(),
                response.getType(),
                response.getTitle(),
                response.getMessage(),
                response.getRelatedEntityType(),
                response.getRelatedEntityId(),
                response.getDeepLink(),
                response.getActionType(),
                response.isRead(),
                response.getReadAt(),
                response.getCreatedAt(),
                response.getUnreadCount(),
                response.getRealtimeEventKind()
        );
    }
}
