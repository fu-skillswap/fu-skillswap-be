package com.fptu.exe.skillswap.modules.notification.port;

import com.fptu.exe.skillswap.modules.notification.domain.EmailOutbox;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailOutboxPort {

    boolean existsById(UUID id);

    Optional<EmailOutbox> findById(UUID id);

    Optional<EmailOutbox> findByIdForUpdate(UUID id);

    EmailOutbox save(EmailOutbox emailOutbox);

    Page<EmailOutbox> searchForAdmin(
            NotificationStatus status,
            String templateCode,
            String toEmailPattern,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            Pageable pageable
    );
}
