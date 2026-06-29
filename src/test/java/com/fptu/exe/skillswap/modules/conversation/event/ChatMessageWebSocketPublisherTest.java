package com.fptu.exe.skillswap.modules.conversation.event;

import com.fptu.exe.skillswap.infrastructure.websocket.RealtimeMessageType;
import com.fptu.exe.skillswap.infrastructure.websocket.RealtimePushService;
import com.fptu.exe.skillswap.modules.conversation.domain.MessageType;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationType;
import com.fptu.exe.skillswap.modules.conversation.dto.event.ChatMessageEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatMessageWebSocketPublisherTest {

    @Mock
    private RealtimePushService realtimePushService;

    @InjectMocks
    private ChatMessageWebSocketPublisher publisher;

    @Test
    void handleChatMessageSaved_shouldPushToAllRecipients() {
        UUID recipientA = UUID.randomUUID();
        UUID recipientB = UUID.randomUUID();
        ChatMessageEvent event = ChatMessageEvent.builder()
                .conversationId(UUID.randomUUID())
                .messageId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .senderName("Nguyen Van A")
                .messageType(MessageType.TEXT)
                .content("hello")
                .createdAt(LocalDateTime.now())
                .conversationType(ConversationType.DIRECT)
                .isSelf(false)
                .unreadCount(1L)
                .build();

        publisher.handleChatMessageSaved(new ChatMessageSavedEvent(
                event,
                List.of(
                        new ChatMessageRealtimeDelivery(recipientA, event),
                        new ChatMessageRealtimeDelivery(recipientB, event)
                )
        ));

        verify(realtimePushService).pushToUser(recipientA, RealtimeMessageType.CHAT_MESSAGE_CREATED, event);
        verify(realtimePushService).pushToUser(recipientB, RealtimeMessageType.CHAT_MESSAGE_CREATED, event);
    }
}
