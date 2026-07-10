package com.fptu.exe.skillswap.modules.conversation.repository;

import com.fptu.exe.skillswap.modules.conversation.domain.Conversation;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID>, ConversationRepositoryCustom {
    Optional<Conversation> findBySourceTypeAndSourceId(ConversationSourceType sourceType, UUID sourceId);

    java.util.List<Conversation> findBySourceTypeAndSourceIdIn(ConversationSourceType sourceType, java.util.List<UUID> sourceIds);

    @Query("SELECT c FROM Conversation c JOIN ConversationParticipant cp ON c.id = cp.conversation.id WHERE cp.user.id = :userId ORDER BY c.lastMessageAt DESC NULLS LAST, c.createdAt DESC")
    Page<Conversation> findByParticipantUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            select c
            from Conversation c
            where c.type = :type
              and c.status = :status
              and exists (
                    select 1
                    from ConversationParticipant cp1
                    where cp1.conversation.id = c.id
                      and cp1.user.id = :firstUserId
              )
              and exists (
                    select 1
                    from ConversationParticipant cp2
                    where cp2.conversation.id = c.id
                      and cp2.user.id = :secondUserId
              )
            order by c.lastMessageAt desc nulls last, c.createdAt desc
            """)
    List<Conversation> findDirectActiveByParticipantPair(
            @Param("firstUserId") UUID firstUserId,
            @Param("secondUserId") UUID secondUserId,
            @Param("type") ConversationType type,
            @Param("status") ConversationStatus status
    );
}
