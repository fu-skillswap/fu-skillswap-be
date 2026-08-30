package com.fptu.exe.skillswap.modules.notification.port;

import com.fptu.exe.skillswap.modules.notification.NotificationType;

import java.util.UUID;

public interface NotificationPort {
    void createNotification(UUID recipientUserId, NotificationType type, String title, String message, String relatedEntityType, UUID relatedEntityId, String deepLink);
    void sendHtmlEmail(String toEmail, String subject, String htmlContent);
}
