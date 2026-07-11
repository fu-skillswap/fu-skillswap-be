package com.fptu.exe.skillswap.modules.notification.event;

import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

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
}
