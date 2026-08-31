package com.fptu.exe.skillswap.modules.chat.repository;

import com.fptu.exe.skillswap.modules.chat.domain.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, UUID>, MessageRepositoryCustom {
    @EntityGraph(attributePaths = {"sender"})
    Page<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    @org.springframework.data.jpa.repository.Query("select m from Message m where m.conversation.id = :conversationId order by m.createdAt desc, m.id desc")
    List<Message> findLatestMessages(@org.springframework.data.repository.query.Param("conversationId") UUID conversationId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    @org.springframework.data.jpa.repository.Query("select m from Message m where m.conversation.id = :conversationId and (m.createdAt < :createdAt or (m.createdAt = :createdAt and m.id < :messageId)) order by m.createdAt desc, m.id desc")
    List<Message> findMessagesBefore(@org.springframework.data.repository.query.Param("conversationId") UUID conversationId,
                                     @org.springframework.data.repository.query.Param("createdAt") LocalDateTime createdAt,
                                     @org.springframework.data.repository.query.Param("messageId") UUID messageId,
                                     Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    @org.springframework.data.jpa.repository.Query("select m from Message m where m.conversation.id = :conversationId and m.sequence < :sequence order by m.sequence desc")
    List<Message> findMessagesBeforeSequence(@org.springframework.data.repository.query.Param("conversationId") UUID conversationId,
                                             @org.springframework.data.repository.query.Param("sequence") Long sequence, Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    @org.springframework.data.jpa.repository.Query("select m from Message m where m.conversation.id = :conversationId and m.sequence > :sequence order by m.sequence asc")
    List<Message> findMessagesAfterSequence(@org.springframework.data.repository.query.Param("conversationId") UUID conversationId,
                                            @org.springframework.data.repository.query.Param("sequence") Long sequence, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
        select count(m) from Message m
        where m.conversation.id = :conversationId
          and (m.sender is null or m.sender.id <> :userId)
          and m.sequence > :lastReadSequence
    """)
    long countUnreadMessages(@org.springframework.data.repository.query.Param("conversationId") UUID conversationId,
                             @org.springframework.data.repository.query.Param("userId") UUID userId,
                             @org.springframework.data.repository.query.Param("lastReadSequence") long lastReadSequence);

    @org.springframework.data.jpa.repository.Query("""
        select m.conversation.id, count(m)
        from Message m
        join ConversationParticipant cp on cp.conversation.id = m.conversation.id
        where cp.user.id = :userId
          and m.conversation.id in :conversationIds
          and (m.sender.id is null or m.sender.id <> :userId)
          and m.sequence > cp.lastReadSequence
        group by m.conversation.id
    """)
    java.util.List<Object[]> countUnreadMessagesBatch(@org.springframework.data.repository.query.Param("conversationIds") java.util.List<UUID> conversationIds,
                                                    @org.springframework.data.repository.query.Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Query("""
        select cp.user.id, count(m)
        from ConversationParticipant cp
        left join Message m on m.conversation.id = cp.conversation.id 
                            and m.sequence > cp.lastReadSequence
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
          and m.sequence > cp.lastReadSequence
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

    Optional<Message> findByConversationIdAndSenderIdAndClientMessageId(UUID conversationId, UUID senderId, UUID clientMessageId);

    Optional<Message> findByBookingIdAndSystemEventType(UUID bookingId, String systemEventType);

    @EntityGraph(attributePaths = {"sender"})
    @org.springframework.data.jpa.repository.Query("select m from Message m where m.conversation.id=:conversationId and (:before is null or m.sequence < :before) and (:after is null or m.sequence > :after) order by m.sequence desc")
    List<Message> findByConversationSequenceWindow(@org.springframework.data.repository.query.Param("conversationId") UUID conversationId, @org.springframework.data.repository.query.Param("before") Long before, @org.springframework.data.repository.query.Param("after") Long after, org.springframework.data.domain.Pageable pageable);
}
