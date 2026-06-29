package com.fptu.exe.skillswap.modules.notification.event;

import com.fptu.exe.skillswap.modules.notification.dto.response.NotificationResponse;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;

import java.util.UUID;

public record NotificationCreatedEvent(
        UUID recipientUserId,
        NotificationResponse notification,
        NotificationType notificationType
) {
}
