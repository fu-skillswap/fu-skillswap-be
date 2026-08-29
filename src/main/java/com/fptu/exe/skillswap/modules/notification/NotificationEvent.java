package com.fptu.exe.skillswap.modules.notification;

import java.util.UUID;

public record NotificationEvent(
    UUID recipientUserId,
    NotificationType type,
    String title,
    String message,
    String relatedEntityType,
    UUID relatedEntityId
) {}
