package com.fptu.exe.skillswap.modules.notification.event;

import com.fptu.exe.skillswap.infrastructure.websocket.RealtimeMessageType;
import com.fptu.exe.skillswap.infrastructure.websocket.RealtimePayloads;
import com.fptu.exe.skillswap.infrastructure.websocket.RealtimePushService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationBadgeWebSocketPublisherTest {

    @Mock
    private RealtimePushService realtimePushService;

    @InjectMocks
    private NotificationBadgeWebSocketPublisher publisher;

    @Test
    void handleBadgeChanged_shouldPushBadgeUpdate() {
        UUID recipientId = UUID.randomUUID();

        publisher.handleBadgeChanged(new NotificationBadgeChangedEvent(recipientId, 2L, "READ"));

        verify(realtimePushService).pushToUser(
                recipientId,
                RealtimeMessageType.NOTIFICATION_BADGE_UPDATED,
                new RealtimePayloads.NotificationBadgePayload(2L, "READ")
        );
    }
}
