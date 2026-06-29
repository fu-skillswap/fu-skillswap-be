package com.fptu.exe.skillswap.modules.conversation.event;

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
public class ChatMessageWebSocketPublisher {

    private final RealtimePushService realtimePushService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void handleChatMessageSaved(ChatMessageSavedEvent event) {
        log.info("Publishing ChatMessageEvent to raw WebSockets after transaction commit for message: {}", event.getEvent().messageId());
        for (ChatMessageRealtimeDelivery delivery : event.getDeliveries()) {
            try {
                realtimePushService.pushToUser(
                        delivery.recipientUserId(),
                        RealtimeMessageType.CHAT_MESSAGE_CREATED,
                        delivery.event()
                );
            } catch (Exception ex) {
                log.error("Failed to send WebSocket message to user: {}", delivery.recipientUserId(), ex);
            }
        }
    }
}
