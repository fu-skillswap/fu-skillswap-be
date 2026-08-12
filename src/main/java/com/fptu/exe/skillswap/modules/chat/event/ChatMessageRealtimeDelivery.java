package com.fptu.exe.skillswap.modules.chat.event;

import com.fptu.exe.skillswap.modules.chat.dto.event.ChatMessageEvent;

import java.util.UUID;

public record ChatMessageRealtimeDelivery(
        UUID recipientUserId,
        ChatMessageEvent event
) {
}
