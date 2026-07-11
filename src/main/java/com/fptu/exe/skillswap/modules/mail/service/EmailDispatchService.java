package com.fptu.exe.skillswap.modules.mail.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.modules.notification.domain.EmailOutbox;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationStatus;
import com.fptu.exe.skillswap.modules.notification.repository.EmailOutboxRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
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
    private final ObjectMapper objectMapper;

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
                .payloadData(serializePayload(new EmailPayload(
                        toEmail,
                        subject,
                        htmlBody,
                        plainTextFallback,
                        templateCode
                )))
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
            EmailPayload payload = deserializePayload(outbox);
            boolean sent = emailService.sendHtmlEmail(
                    payload.toEmail(),
                    payload.subject(),
                    payload.htmlBody(),
                    payload.plainTextFallback()
            );
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

    private String serializePayload(EmailPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BaseException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Không thể serialize email payload");
        }
    }

    private EmailPayload deserializePayload(EmailOutbox outbox) {
        if (outbox.getPayloadData() == null || outbox.getPayloadData().isBlank()) {
            log.warn("Email outbox {} is missing payload_data. Falling back to legacy columns.", outbox.getId());
            return new EmailPayload(
                    outbox.getToEmail(),
                    outbox.getSubject(),
                    outbox.getBody(),
                    outbox.getBody(),
                    outbox.getTemplateCode()
            );
        }
        try {
            return objectMapper.readValue(outbox.getPayloadData(), EmailPayload.class);
        } catch (JsonProcessingException ex) {
            throw new BaseException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Không thể deserialize email payload");
        }
    }

    private record EmailPayload(
            String toEmail,
            String subject,
            String htmlBody,
            String plainTextFallback,
            String templateCode
    ) {
    }
}
