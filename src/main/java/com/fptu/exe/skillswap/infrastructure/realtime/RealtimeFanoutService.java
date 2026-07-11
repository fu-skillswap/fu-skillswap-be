package com.fptu.exe.skillswap.infrastructure.realtime;

import com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent;
import com.fptu.exe.skillswap.modules.conversation.dto.event.ChatMessageEvent;
import com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.notification.dto.response.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RealtimeFanoutService {

    public static final String USER_QUEUE_CHAT_MESSAGES = "/queue/chat/messages";
    public static final String USER_QUEUE_CHAT_INBOX = "/queue/chat/inbox";
    public static final String USER_QUEUE_CHAT_UNREAD = "/queue/chat/unread";
    public static final String USER_QUEUE_BOOKING_STATUS = "/queue/bookings/status";
    public static final String USER_QUEUE_NOTIFICATION_ITEMS = "/queue/notifications/items";
    public static final String USER_QUEUE_NOTIFICATION_BADGE = "/queue/notifications/badge";

    private final ObjectProvider<SimpMessagingTemplate> simpMessagingTemplateProvider;

    public void pushChatMessage(UUID recipientUserId, ChatMessageEvent payload) {
        sendStomp(recipientUserId, USER_QUEUE_CHAT_MESSAGES, payload);
    }

    public void pushConversationSummary(UUID recipientUserId, ConversationResponse payload) {
        sendStomp(recipientUserId, USER_QUEUE_CHAT_INBOX, payload);
    }

    public void pushChatUnread(UUID recipientUserId, long totalUnreadCount) {
        Map<String, Long> payload = Map.of("totalUnreadCount", totalUnreadCount);
        sendStomp(recipientUserId, USER_QUEUE_CHAT_UNREAD, payload);
    }

    public void pushBookingStatus(UUID recipientUserId, BookingStatusUpdatedEvent payload) {
        sendStomp(recipientUserId, USER_QUEUE_BOOKING_STATUS, payload);
    }

    public void pushNotificationItem(UUID recipientUserId, NotificationResponse payload) {
        sendStomp(recipientUserId, USER_QUEUE_NOTIFICATION_ITEMS, payload);
    }

    public void pushNotificationBadge(UUID recipientUserId, long unreadCount, String eventKind) {
        Map<String, Object> payload = Map.of(
                "unreadCount", unreadCount,
                "eventKind", eventKind
        );
        sendStomp(recipientUserId, USER_QUEUE_NOTIFICATION_BADGE, payload);
    }

    private void sendStomp(UUID recipientUserId, String destination, Object payload) {
        if (recipientUserId == null) {
            return;
        }
        SimpMessagingTemplate template = simpMessagingTemplateProvider.getIfAvailable();
        if (template == null) {
            return;
        }
        template.convertAndSendToUser(recipientUserId.toString(), destination, payload);
    }
}
