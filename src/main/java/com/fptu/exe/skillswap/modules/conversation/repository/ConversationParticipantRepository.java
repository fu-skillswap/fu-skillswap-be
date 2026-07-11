package com.fptu.exe.skillswap.modules.conversation.repository;

import com.fptu.exe.skillswap.modules.conversation.domain.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {
    List<ConversationParticipant> findByUserId(UUID userId);
    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);
    java.util.Optional<ConversationParticipant> findByConversationIdAndUserId(UUID conversationId, UUID userId);
    List<ConversationParticipant> findByConversationIdIn(List<UUID> conversationIds);
    List<ConversationParticipant> findByConversationId(UUID conversationId);

    @org.springframework.data.jpa.repository.Query("""
        select cp from ConversationParticipant cp
        join fetch cp.user
        where cp.conversation.id in :conversationIds
    """)
    List<ConversationParticipant> findByConversationIdInWithUser(@org.springframework.data.repository.query.Param("conversationIds") java.util.Collection<UUID> conversationIds);
}
