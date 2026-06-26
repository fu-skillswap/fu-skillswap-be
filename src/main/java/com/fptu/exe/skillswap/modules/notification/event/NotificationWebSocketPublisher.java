package com.fptu.exe.skillswap.modules.notification.event;

import com.fptu.exe.skillswap.infrastructure.websocket.RealtimeMessageType;
import com.fptu.exe.skillswap.infrastructure.websocket.RealtimePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWebSocketPublisher {

    private final RealtimePushService realtimePushService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void handleNotificationCreated(NotificationCreatedEvent event) {
        if (event == null || event.recipientUserId() == null || event.notification() == null) {
            return;
        }
        try {
            realtimePushService.pushToUser(
                    event.recipientUserId(),
                    RealtimeMessageType.NEW_NOTIFICATION,
                    event.notification()
            );
        } catch (Exception ex) {
            log.error("Failed to push realtime notification to user={}", event.recipientUserId(), ex);
        }
    }
}
