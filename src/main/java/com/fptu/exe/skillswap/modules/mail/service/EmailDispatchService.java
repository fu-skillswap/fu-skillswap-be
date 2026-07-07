package com.fptu.exe.skillswap.modules.mail.service;

import com.fptu.exe.skillswap.modules.notification.domain.EmailOutbox;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationStatus;
import com.fptu.exe.skillswap.modules.notification.repository.EmailOutboxRepository;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDispatchService {

    private final EmailService emailService;
    private final EmailOutboxRepository emailOutboxRepository;

    @Transactional
    public boolean sendHtmlOnce(
            String dedupeKey,
            String toEmail,
            String subject,
            String htmlBody,
            String plainTextFallback,
            String templateCode
    ) {
        if (dedupeKey == null || dedupeKey.isBlank()) {
            throw new IllegalArgumentException("dedupeKey is required");
        }
        if (emailOutboxRepository.existsByDedupeKey(dedupeKey)) {
            log.debug("Skipping duplicate email. dedupeKey={}", dedupeKey);
            return false;
        }

        EmailOutbox outbox = EmailOutbox.builder()
                .toEmail(toEmail)
                .subject(subject)
                .body(htmlBody)
                .templateCode(templateCode)
                .dedupeKey(dedupeKey)
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .build();

        try {
            outbox = emailOutboxRepository.saveAndFlush(outbox);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Skipping duplicate email after unique constraint. dedupeKey={}", dedupeKey);
            return false;
        }

        boolean sent = emailService.sendHtmlEmail(toEmail, subject, htmlBody, plainTextFallback);
        outbox.setStatus(sent ? NotificationStatus.SENT : NotificationStatus.FAILED);
        outbox.setSentAt(sent ? DateTimeUtil.now() : null);
        outbox.setLastError(sent ? null : "EmailService returned false");
        emailOutboxRepository.save(outbox);
        return sent;
    }
}
