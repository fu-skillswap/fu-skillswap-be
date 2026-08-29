package com.fptu.exe.skillswap.modules.notification.event;

import com.fptu.exe.skillswap.modules.notification.NotificationEvent;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    void handleNotificationEvent_shouldCallNotificationService() {
        UUID recipientId = UUID.randomUUID();
        NotificationType type = NotificationType.BOOKING_ACCEPTED;
        String title = "Đã chấp nhận";
        String message = "Mentor đã chấp nhận yêu cầu của bạn.";
        String entityType = "BOOKING";
        UUID entityId = UUID.randomUUID();

        NotificationEvent event = new NotificationEvent(
                recipientId, type, title, message, entityType, entityId
        );

        listener.handleNotificationEvent(event);

        verify(notificationService).createNotification(
                recipientId, type, title, message, entityType, entityId
        );
    }

    @Test
    void handleNotificationEvent_shouldPropagateFailureSoBookingTransactionRollsBack() {
        NotificationEvent event = new NotificationEvent(UUID.randomUUID(), NotificationType.BOOKING_ISSUE_REPORTED,
                "Dispute", "message", "BOOKING", UUID.randomUUID());
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(notificationService).createNotification(event.recipientUserId(), event.type(), event.title(),
                        event.message(), event.relatedEntityType(), event.relatedEntityId());

        assertThrows(IllegalStateException.class, () -> listener.handleNotificationEvent(event));
    }
}
