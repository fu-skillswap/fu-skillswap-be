package com.fptu.exe.skillswap.modules.notification.event;

import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationEvent(NotificationEvent event) {
        try {
            notificationService.createNotification(
                    event.recipientUserId(),
                    event.type(),
                    event.title(),
                    event.message(),
                    event.relatedEntityType(),
                    event.relatedEntityId()
            );
        } catch (Exception ex) {
            log.error("Failed to create background notification. recipient={}, type={}", 
                    event.recipientUserId(), event.type(), ex);
        }
    }
}
