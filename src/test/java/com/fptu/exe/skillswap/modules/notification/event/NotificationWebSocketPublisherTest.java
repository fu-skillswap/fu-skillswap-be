package com.fptu.exe.skillswap.modules.notification.event;

import com.fptu.exe.skillswap.infrastructure.websocket.RealtimeMessageType;
import com.fptu.exe.skillswap.infrastructure.websocket.RealtimePushService;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.dto.response.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationWebSocketPublisherTest {

    @Mock
    private RealtimePushService realtimePushService;

    @InjectMocks
    private NotificationWebSocketPublisher publisher;

    @Test
    void handleNotificationCreated_shouldPushToRecipient() {
        UUID recipientId = UUID.randomUUID();
        NotificationResponse notification = NotificationResponse.builder()
                .notificationId(UUID.randomUUID())
                .type("BOOKING_ACCEPTED")
                .title("Da chap nhan")
                .message("Mentor da chap nhan lich")
                .relatedEntityType("BOOKING")
                .relatedEntityId(UUID.randomUUID())
                .createdAt(LocalDateTime.now())
                .unreadCount(3L)
                .realtimeEventKind("CREATED")
                .build();

        publisher.handleNotificationCreated(new NotificationCreatedEvent(recipientId, notification, NotificationType.BOOKING_ACCEPTED));

        verify(realtimePushService).pushToUser(recipientId, RealtimeMessageType.NEW_NOTIFICATION, notification);
    }

    @Test
    void handleNotificationCreated_shouldIgnoreNonImportantType() {
        UUID recipientId = UUID.randomUUID();
        NotificationResponse notification = NotificationResponse.builder()
                .notificationId(UUID.randomUUID())
                .type("FORUM_POST_COMMENTED")
                .title("Bai viet co binh luan moi")
                .message("Co binh luan moi")
                .createdAt(LocalDateTime.now())
                .build();

        publisher.handleNotificationCreated(new NotificationCreatedEvent(recipientId, notification, NotificationType.FORUM_POST_COMMENTED));

        verify(realtimePushService, org.mockito.Mockito.never())
                .pushToUser(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }
}
