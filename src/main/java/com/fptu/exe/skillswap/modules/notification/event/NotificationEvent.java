package com.fptu.exe.skillswap.modules.notification.event;

import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import java.util.UUID;

public record NotificationEvent(
    UUID recipientUserId,
    NotificationType type,
    String title,
    String message,
    String relatedEntityType,
    UUID relatedEntityId
) {}
