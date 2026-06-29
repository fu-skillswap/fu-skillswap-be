package com.fptu.exe.skillswap.modules.notification.event;

import java.util.UUID;

public record NotificationBadgeChangedEvent(
        UUID recipientUserId,
        long unreadCount,
        String eventKind
) {
}
