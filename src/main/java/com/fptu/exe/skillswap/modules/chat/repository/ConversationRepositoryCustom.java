package com.fptu.exe.skillswap.modules.chat.repository;

import com.fptu.exe.skillswap.modules.chat.domain.Conversation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ConversationRepositoryCustom {

    List<Conversation> findConversationWindowByParticipant(
            UUID userId,
            LocalDateTime cursorActivityAt,
            UUID cursorConversationId,
            int fetchLimit
    );
}
