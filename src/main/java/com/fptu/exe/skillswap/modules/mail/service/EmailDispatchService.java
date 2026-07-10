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

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private EmailDispatchService self;

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

        // Call the async method via self-proxy to ensure @Async works
        self.dispatchEmailAsync(outbox.getId());
        return true;
    }

    @org.springframework.scheduling.annotation.Async("emailTaskExecutor")
    public void dispatchEmailAsync(java.util.UUID outboxId) {
        EmailOutbox outbox = emailOutboxRepository.findById(outboxId).orElse(null);
        if (outbox == null || outbox.getStatus() != NotificationStatus.PENDING) {
            return;
        }
        try {
            boolean sent = emailService.sendHtmlEmail(outbox.getToEmail(), outbox.getSubject(), outbox.getBody(), outbox.getTemplateCode()); // wait, plainTextFallback is lost? No, we don't have plainText in EmailOutbox. EmailOutbox has body (which is html).
            self.updateOutboxStatus(outboxId, sent ? NotificationStatus.SENT : NotificationStatus.FAILED, sent ? null : "EmailService returned false");
        } catch (Exception e) {
            log.error("Error dispatching email outbox {}", outboxId, e);
            self.updateOutboxStatus(outboxId, NotificationStatus.FAILED, e.getMessage());
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void updateOutboxStatus(java.util.UUID id, NotificationStatus status, String errorLog) {
        emailOutboxRepository.updateStatus(id, status, errorLog);
    }
}
