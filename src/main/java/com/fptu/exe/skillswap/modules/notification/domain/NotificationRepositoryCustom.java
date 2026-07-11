package com.fptu.exe.skillswap.modules.notification.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepositoryCustom {

    List<Notification> findNotificationWindow(
            UUID recipientUserId,
            boolean unreadOnly,
            LocalDateTime cursorCreatedAt,
            UUID cursorNotificationId,
            int fetchLimit
    );
}
