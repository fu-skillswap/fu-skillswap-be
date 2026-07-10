package com.fptu.exe.skillswap.modules.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, NotificationRepositoryCustom {

    Page<Notification> findByRecipientUserId(UUID recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndReadAtIsNull(UUID recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipientUser.id = :recipientUserId AND n.readAt IS NULL")
    int markAllAsRead(@Param("recipientUserId") UUID recipientUserId, @Param("now") LocalDateTime now);
}
