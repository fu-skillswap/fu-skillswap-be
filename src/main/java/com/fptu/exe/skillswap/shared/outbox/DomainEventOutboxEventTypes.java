package com.fptu.exe.skillswap.shared.outbox;

public final class DomainEventOutboxEventTypes {

    public static final String CHAT_MESSAGE_CREATED = "chat.message.created";
    public static final String CHAT_CONVERSATION_UPDATED = "chat.conversation.updated";
    public static final String CHAT_UNREAD_COUNT_UPDATED = "chat.unread-count.updated";
    public static final String NOTIFICATION_CREATED = "notification.created";
    public static final String NOTIFICATION_BADGE_UPDATED = "notification.badge.updated";

    private DomainEventOutboxEventTypes() {
    }
}
