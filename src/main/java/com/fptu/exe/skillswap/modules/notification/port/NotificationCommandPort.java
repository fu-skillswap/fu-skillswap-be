package com.fptu.exe.skillswap.modules.notification.port;

import java.util.UUID;

/** Immutable notification intent accepted by the Notification module. */
public interface NotificationCommandPort {

    void publish(NotificationIntent intent);

    record NotificationIntent(
            UUID recipientUserId,
            String type,
            String title,
            String message,
            String relatedEntityType,
            UUID relatedEntityId,
            String deepLink
    ) {
    }
}
