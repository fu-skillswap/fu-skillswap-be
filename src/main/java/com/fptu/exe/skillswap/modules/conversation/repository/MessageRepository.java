package com.fptu.exe.skillswap.modules.conversation.repository;

import com.fptu.exe.skillswap.modules.conversation.domain.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID>, MessageRepositoryCustom {
    @EntityGraph(attributePaths = {"sender"})
    Page<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
        select count(m) from Message m
        where m.conversation.id = :conversationId
          and (m.sender is null or m.sender.id <> :userId)
          and m.createdAt > :lastReadAt
    """)
    long countUnreadMessages(@org.springframework.data.repository.query.Param("conversationId") UUID conversationId,
                             @org.springframework.data.repository.query.Param("userId") UUID userId,
                             @org.springframework.data.repository.query.Param("lastReadAt") java.time.LocalDateTime lastReadAt);

    @org.springframework.data.jpa.repository.Query("""
        select m.conversation.id, count(m)
        from Message m
        join ConversationParticipant cp on cp.conversation.id = m.conversation.id
        where cp.user.id = :userId
          and m.conversation.id in :conversationIds
          and (m.sender.id is null or m.sender.id <> :userId)
          and m.createdAt > coalesce(cp.lastReadAt, cp.joinedAt)
        group by m.conversation.id
    """)
    java.util.List<Object[]> countUnreadMessagesBatch(@org.springframework.data.repository.query.Param("conversationIds") java.util.List<UUID> conversationIds,
                                                    @org.springframework.data.repository.query.Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Query("""
        select cp.user.id, count(m)
        from ConversationParticipant cp
        left join Message m on m.conversation.id = cp.conversation.id 
                            and m.createdAt > coalesce(cp.lastReadAt, cp.joinedAt) 
                            and (m.sender.id is null or m.sender.id <> cp.user.id)
        where cp.conversation.id = :conversationId
          and cp.user.id in :userIds
        group by cp.user.id
    """)
    java.util.List<Object[]> countUnreadMessagesForParticipants(
            @org.springframework.data.repository.query.Param("conversationId") UUID conversationId,
            @org.springframework.data.repository.query.Param("userIds") java.util.List<UUID> userIds
    );

    @org.springframework.data.jpa.repository.Query("""
        select count(m)
        from Message m
        join ConversationParticipant cp on cp.conversation.id = m.conversation.id
        where cp.user.id = :userId
          and (m.sender is null or m.sender.id <> :userId)
          and m.createdAt > coalesce(cp.lastReadAt, cp.joinedAt)
    """)
    long countTotalUnreadMessages(@org.springframework.data.repository.query.Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Query("""
        select count(m) > 0
        from Message m
        where m.conversation.id = :conversationId
          and m.sender.id = :senderId
          and lower(m.content) = lower(:content)
          and m.createdAt >= :createdAfter
    """)
    boolean existsRecentDuplicateMessage(
            @org.springframework.data.repository.query.Param("conversationId") UUID conversationId,
            @org.springframework.data.repository.query.Param("senderId") UUID senderId,
            @org.springframework.data.repository.query.Param("content") String content,
            @org.springframework.data.repository.query.Param("createdAfter") LocalDateTime createdAfter
    );
}
