package com.fptu.exe.skillswap.modules.notification.service;

import com.fptu.exe.skillswap.modules.notification.domain.EmailOutbox;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationStatus;
import com.fptu.exe.skillswap.modules.notification.port.EmailOutboxPort;
import com.fptu.exe.skillswap.modules.notification.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailOutboxPortImpl implements EmailOutboxPort {

    private final EmailOutboxRepository emailOutboxRepository;

    @Override
    public boolean existsById(UUID id) {
        return id != null && emailOutboxRepository.existsById(id);
    }

    @Override
    public Optional<EmailOutbox> findById(UUID id) {
        return id == null ? Optional.empty() : emailOutboxRepository.findById(id);
    }

    @Override
    public Optional<EmailOutbox> findByIdForUpdate(UUID id) {
        return id == null ? Optional.empty() : emailOutboxRepository.findByIdForUpdate(id);
    }

    @Override
    @Transactional
    public EmailOutbox save(EmailOutbox emailOutbox) {
        return emailOutboxRepository.save(emailOutbox);
    }

    @Override
    public Page<EmailOutbox> searchForAdmin(
            NotificationStatus status,
            String templateCode,
            String toEmailPattern,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            Pageable pageable
    ) {
        return emailOutboxRepository.searchForAdmin(status, templateCode, toEmailPattern, fromTime, toTime, pageable);
    }
}
