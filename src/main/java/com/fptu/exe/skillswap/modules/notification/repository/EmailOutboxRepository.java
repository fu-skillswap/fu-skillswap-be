package com.fptu.exe.skillswap.modules.notification.repository;

import com.fptu.exe.skillswap.modules.notification.domain.EmailOutbox;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select emailOutbox from EmailOutbox emailOutbox where emailOutbox.id = :emailOutboxId")
    Optional<EmailOutbox> findByIdForUpdate(@Param("emailOutboxId") UUID emailOutboxId);

    boolean existsByDedupeKey(String dedupeKey);

    Optional<EmailOutbox> findByDedupeKey(String dedupeKey);

    @Query(value = """
            select emailOutbox
            from EmailOutbox emailOutbox
            where (:status is null or emailOutbox.status = :status)
              and (:templateCode is null or lower(emailOutbox.templateCode) = lower(:templateCode))
              and (:toEmailPattern is null or lower(emailOutbox.toEmail) like :toEmailPattern)
              and (:fromTime is null or emailOutbox.createdAt >= :fromTime)
              and (:toTime is null or emailOutbox.createdAt <= :toTime)
            """, countQuery = """
            select count(emailOutbox.id)
            from EmailOutbox emailOutbox
            where (:status is null or emailOutbox.status = :status)
              and (:templateCode is null or lower(emailOutbox.templateCode) = lower(:templateCode))
              and (:toEmailPattern is null or lower(emailOutbox.toEmail) like :toEmailPattern)
              and (:fromTime is null or emailOutbox.createdAt >= :fromTime)
              and (:toTime is null or emailOutbox.createdAt <= :toTime)
            """)
    Page<EmailOutbox> searchForAdmin(
            @Param("status") NotificationStatus status,
            @Param("templateCode") String templateCode,
            @Param("toEmailPattern") String toEmailPattern,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            Pageable pageable
    );

    java.util.List<EmailOutbox> findTop10ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(NotificationStatus status, int retryCount);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
        update EmailOutbox e 
        set e.status = :status, e.lastError = :errorLog, e.retryCount = e.retryCount + 1, e.sentAt = case when :status = 'SENT' then current_timestamp else e.sentAt end
        where e.id = :id
    """)
    void updateStatus(@Param("id") UUID id, @Param("status") NotificationStatus status, @Param("errorLog") String errorLog);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
        update EmailOutbox e 
        set e.status = 'FATAL_ERROR', e.lastError = 'Exceeded maximum retries'
        where e.status = 'FAILED' and e.retryCount >= :maxRetries
    """)
    int updateFailedToFatalError(@Param("maxRetries") int maxRetries);
}
