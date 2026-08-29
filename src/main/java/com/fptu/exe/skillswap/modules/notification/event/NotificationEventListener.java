package com.fptu.exe.skillswap.modules.notification.event;

import com.fptu.exe.skillswap.modules.notification.NotificationEvent;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    // Notification rows and their realtime outbox events must commit with the
    // business change. Realtime delivery itself is handled asynchronously by the
    // durable domain outbox publisher.
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleNotificationEvent(NotificationEvent event) {
        notificationService.createNotification(
                event.recipientUserId(),
                event.type(),
                event.title(),
                event.message(),
                event.relatedEntityType(),
                event.relatedEntityId()
        );
    }
}
