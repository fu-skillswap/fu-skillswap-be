package com.fptu.exe.skillswap.modules.conversation.repository;

import com.fptu.exe.skillswap.modules.conversation.domain.Conversation;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Optional<Conversation> findBySourceTypeAndSourceId(ConversationSourceType sourceType, UUID sourceId);

    java.util.List<Conversation> findBySourceTypeAndSourceIdIn(ConversationSourceType sourceType, java.util.List<UUID> sourceIds);

    @Query("SELECT c FROM Conversation c JOIN ConversationParticipant cp ON c.id = cp.conversation.id WHERE cp.user.id = :userId ORDER BY c.lastMessageAt DESC NULLS LAST, c.createdAt DESC")
    Page<Conversation> findByParticipantUserId(@Param("userId") UUID userId, Pageable pageable);
}
