package com.fptu.exe.skillswap.modules.notification.service.channel;

import com.fptu.exe.skillswap.modules.notification.NotificationType;

import java.util.UUID;

public interface NotificationChannelSender {

    String getChannelName(); // "EMAIL", "IN_APP", "PUSH", "SMS"

    boolean supports(NotificationType type);

    boolean send(UUID recipientUserId, NotificationType type, String title, String content, String deepLink);
}
