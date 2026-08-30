package com.fptu.exe.skillswap.modules.notification.service;

import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.modules.notification.port.NotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPortImpl implements NotificationPort {

    private final NotificationService notificationService;
    private final EmailDispatchService emailDispatchService;

    @Override
    public void createNotification(UUID recipientUserId, NotificationType type, String title, String message, String relatedEntityType, UUID relatedEntityId, String deepLink) {
        notificationService.createNotification(recipientUserId, type, title, message, relatedEntityType, relatedEntityId, deepLink);
    }

    @Override
    public void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        emailDispatchService.dispatch(toEmail, subject, htmlContent);
    }
}
