package com.fptu.exe.skillswap.modules.chat.repository;
import com.fptu.exe.skillswap.modules.chat.domain.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.fptu.exe.skillswap.modules.chat.domain.ChatAttachmentState;
public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, UUID> {
 List<ChatAttachment> findByMessageId(UUID messageId);
 List<ChatAttachment> findByMessageIdIn(Collection<UUID> messageIds);
 @org.springframework.data.jpa.repository.Query("select coalesce(sum(a.sizeBytes),0) from ChatAttachment a where a.message.sender.id=:userId and a.createdAt>=:since")
 long sumUploadedBytesByUserSince(@org.springframework.data.repository.query.Param("userId") UUID userId, @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

 @Query("select a from ChatAttachment a where a.state = :state and a.expiresAt <= :now order by a.expiresAt asc")
 List<ChatAttachment> findExpiredForTransition(@Param("state") ChatAttachmentState state,
                                                @Param("now") LocalDateTime now,
                                                Pageable pageable);

 @Query("""
        select a from ChatAttachment a
        where a.state in :states
          and a.deletedAt is null
          and a.expiresAt <= :cutoff
          and (a.holdUntil is null or a.holdUntil <= :now)
        order by a.expiresAt asc
        """)
 List<ChatAttachment> findReadyForPhysicalDeletion(@Param("states") Collection<ChatAttachmentState> states,
                                                   @Param("cutoff") LocalDateTime cutoff,
                                                   @Param("now") LocalDateTime now,
                                                   Pageable pageable);

 @Modifying
 @Query("update ChatAttachment a set a.deletedAt = :deletedAt, a.state = :deletedState where a.id = :id and a.deletedAt is null and a.state in :states and a.expiresAt <= :cutoff and (a.holdUntil is null or a.holdUntil <= :now)")
 int markDeletedIfStillEligible(@Param("id") UUID id,
                                @Param("deletedAt") LocalDateTime deletedAt,
                                @Param("deletedState") ChatAttachmentState deletedState,
                                @Param("states") Collection<ChatAttachmentState> states,
                                @Param("cutoff") LocalDateTime cutoff,
                                @Param("now") LocalDateTime now);
}
