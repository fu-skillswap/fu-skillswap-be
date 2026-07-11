package com.fptu.exe.skillswap.modules.conversation.event;

import com.fptu.exe.skillswap.modules.conversation.dto.event.ChatMessageEvent;

import java.util.UUID;

public record ChatMessageRealtimeDelivery(
        UUID recipientUserId,
        ChatMessageEvent event
) {
}
