package com.fptu.exe.skillswap.modules.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, NotificationRepositoryCustom {

    @Query("SELECT n FROM Notification n WHERE n.recipientUser.id = :recipientUserId AND n.type <> com.fptu.exe.skillswap.modules.notification.domain.NotificationType.CHAT_UNREAD")
    Page<Notification> findByRecipientUserId(@Param("recipientUserId") UUID recipientUserId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.recipientUser.id = :recipientUserId AND n.readAt IS NULL AND n.type <> com.fptu.exe.skillswap.modules.notification.domain.NotificationType.CHAT_UNREAD")
    Page<Notification> findByRecipientUserIdAndReadAtIsNull(@Param("recipientUserId") UUID recipientUserId, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientUser.id = :recipientUserId AND n.readAt IS NULL AND n.type <> com.fptu.exe.skillswap.modules.notification.domain.NotificationType.CHAT_UNREAD")
    long countByRecipientUserIdAndReadAtIsNull(@Param("recipientUserId") UUID recipientUserId);

    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    Optional<Notification> findFirstByRecipientUserIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndReadAtIsNull(UUID recipientUserId, NotificationType type, String relatedEntityType, UUID relatedEntityId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipientUser.id = :recipientUserId AND n.readAt IS NULL")
    int markAllAsRead(@Param("recipientUserId") UUID recipientUserId, @Param("now") LocalDateTime now);

    @Query("select distinct n.recipientUser.id from Notification n where n.readAt is not null and n.readAt < :cutoff order by n.recipientUser.id")
    List<UUID> findUsersWithArchivableNotifications(@Param("cutoff") LocalDateTime cutoff, org.springframework.data.domain.Pageable pageable);

    List<Notification> findTop500ByRecipientUserIdAndReadAtNotNullAndReadAtBeforeOrderByReadAtAscIdAsc(
            UUID recipientUserId, LocalDateTime cutoff);

    @Modifying
    @Query("delete from Notification n where n.id in :ids and n.readAt is not null and n.readAt < :cutoff")
    int deleteArchivedBatch(@Param("ids") List<UUID> ids, @Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query(value = """
            DELETE FROM notifications
            WHERE id IN (
                SELECT id FROM notifications
                WHERE read_at IS NULL AND created_at < :cutoff
                ORDER BY created_at
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteUnreadBefore(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
