package com.fptu.exe.skillswap.modules.conversation.event;

import com.fptu.exe.skillswap.modules.conversation.dto.event.ChatMessageEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ChatMessageSavedEvent {
    private final ChatMessageEvent event;
    private final List<UUID> recipientUserIds;
}
