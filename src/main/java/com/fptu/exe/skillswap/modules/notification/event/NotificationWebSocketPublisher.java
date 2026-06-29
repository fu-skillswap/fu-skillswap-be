package com.fptu.exe.skillswap.modules.notification.event;

import com.fptu.exe.skillswap.infrastructure.websocket.RealtimeMessageType;
import com.fptu.exe.skillswap.infrastructure.websocket.RealtimePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWebSocketPublisher {

    private static final Set<com.fptu.exe.skillswap.modules.notification.domain.NotificationType> IMPORTANT_REALTIME_TYPES = EnumSet.of(
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.MENTOR_VERIFICATION_APPROVED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.MENTOR_VERIFICATION_REJECTED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.MENTOR_VERIFICATION_NEEDS_REVISION,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_REQUEST_CREATED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_ACCEPTED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_PAYMENT_CONFIRMED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_PAYMENT_EXPIRED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_REJECTED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_CANCELLED_BY_MENTEE,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_CANCELLED_BY_MENTOR,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_AUTO_REJECTED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_REQUEST_EXPIRED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_RESCHEDULE_REQUESTED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_RESCHEDULE_ACCEPTED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_RESCHEDULE_REJECTED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.BOOKING_RESCHEDULE_EXPIRED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.MEETING_LINK_UPDATED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.SESSION_COMPLETED,
            com.fptu.exe.skillswap.modules.notification.domain.NotificationType.FEEDBACK_RECEIVED
    );

    private final RealtimePushService realtimePushService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void handleNotificationCreated(NotificationCreatedEvent event) {
        if (event == null || event.recipientUserId() == null || event.notification() == null || event.notificationType() == null) {
            return;
        }
        if (!IMPORTANT_REALTIME_TYPES.contains(event.notificationType())) {
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
