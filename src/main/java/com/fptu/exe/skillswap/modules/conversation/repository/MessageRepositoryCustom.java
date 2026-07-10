package com.fptu.exe.skillswap.modules.conversation.repository;

import com.fptu.exe.skillswap.modules.conversation.domain.Message;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MessageRepositoryCustom {

    List<Message> findMessageWindow(
            UUID conversationId,
            LocalDateTime cursorCreatedAt,
            UUID cursorMessageId,
            int fetchLimit
    );
}
