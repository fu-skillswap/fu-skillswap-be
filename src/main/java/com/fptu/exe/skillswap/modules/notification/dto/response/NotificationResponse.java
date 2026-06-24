package com.fptu.exe.skillswap.modules.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Notification item shown in the current user's notification center. Notifications are created by backend business events such as booking updates or feedback.")
public class NotificationResponse {
    @Schema(description = "Notification ID", example = "019f8234-aaaa-bbbb-cccc-1234567890ab")
    private UUID notificationId;
    @Schema(description = "Notification type code", example = "BOOKING_ACCEPTED")
    private String type;
    @Schema(description = "Short notification title", example = "Yêu cầu đặt lịch đã được chấp nhận")
    private String title;
    @Schema(description = "Main notification message", example = "Nguyen Van B đã chấp nhận lịch mentoring của bạn.")
    private String message;
    @Schema(description = "Related entity type for deep linking", example = "BOOKING")
    private String relatedEntityType;
    @Schema(description = "Related entity ID for deep linking", example = "019f4234-aaaa-bbbb-cccc-1234567890ab")
    private UUID relatedEntityId;
    @Schema(description = "Whether the notification has already been marked as read", example = "false")
    private boolean read;
    @Schema(description = "Timestamp when the notification was marked as read", nullable = true, example = "2026-06-24T12:00:00")
    private LocalDateTime readAt;
    @Schema(description = "Notification creation time", example = "2026-06-24T11:45:00")
    private LocalDateTime createdAt;
}
