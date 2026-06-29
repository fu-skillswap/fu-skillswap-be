package com.fptu.exe.skillswap.modules.notification.event;

import com.fptu.exe.skillswap.infrastructure.websocket.RealtimeMessageType;
import com.fptu.exe.skillswap.infrastructure.websocket.RealtimePayloads;
import com.fptu.exe.skillswap.infrastructure.websocket.RealtimePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationBadgeWebSocketPublisher {

    private final RealtimePushService realtimePushService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void handleBadgeChanged(NotificationBadgeChangedEvent event) {
        if (event == null || event.recipientUserId() == null) {
            return;
        }
        try {
            realtimePushService.pushToUser(
                    event.recipientUserId(),
                    RealtimeMessageType.NOTIFICATION_BADGE_UPDATED,
                    new RealtimePayloads.NotificationBadgePayload(event.unreadCount(), event.eventKind())
            );
        } catch (Exception ex) {
            log.error("Failed to push notification badge update to user={}", event.recipientUserId(), ex);
        }
    }
}
