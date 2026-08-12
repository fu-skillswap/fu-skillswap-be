package com.fptu.exe.skillswap.modules.chat.repository;

import com.fptu.exe.skillswap.modules.chat.domain.ConversationUserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversationUserBlockRepository extends JpaRepository<ConversationUserBlock, UUID> {
    boolean existsByConversationId(UUID conversationId);
    Optional<ConversationUserBlock> findByConversationIdAndBlockerUserId(UUID conversationId, UUID blockerUserId);
    void deleteByConversationIdAndBlockerUserId(UUID conversationId, UUID blockerUserId);
}
