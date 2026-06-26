package com.fptu.exe.skillswap.modules.notification.event;

import com.fptu.exe.skillswap.infrastructure.websocket.RealtimeMessageType;
import com.fptu.exe.skillswap.infrastructure.websocket.RealtimePushService;
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
                .build();

        publisher.handleNotificationCreated(new NotificationCreatedEvent(recipientId, notification));

        verify(realtimePushService).pushToUser(recipientId, RealtimeMessageType.NEW_NOTIFICATION, notification);
    }
}
