package com.fptu.exe.skillswap.modules.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.modules.notification.domain.EmailOutbox;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationStatus;
import com.fptu.exe.skillswap.modules.notification.port.EmailDispatchPort;
import com.fptu.exe.skillswap.modules.notification.repository.EmailOutboxRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDispatchService implements EmailDispatchPort {

    public static final int MAX_SEND_ATTEMPTS = 3;

    private final EmailService emailService;
    private final EmailOutboxRepository emailOutboxRepository;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private EmailDispatchService self;

    @Transactional
    @Override
    public boolean sendHtmlOnce(
            String dedupeKey,
            String toEmail,
            String subject,
            String htmlBody,
            String plainTextFallback,
            String templateCode
    ) {
        java.util.UUID outboxId = queueHtmlOnce(
                dedupeKey,
                toEmail,
                subject,
                htmlBody,
                plainTextFallback,
                templateCode
        );
        if (outboxId == null) {
            return false;
        }
        // This is only a fast path. The durable PENDING record is also picked up by
        // EmailRetryScheduler if the process stops immediately after the transaction.
        self.dispatchEmailAsync(outboxId);
        return true;
    }

    /**
     * Persists the mail intent in the caller's transaction. It deliberately does not
     * trigger an async send, so callers can use it from a BEFORE_COMMIT listener.
     */
    @Transactional
    @Override
    public java.util.UUID queueHtmlOnce(
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
            return null;
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
            return null;
        }
        return outbox.getId();
    }

    @org.springframework.scheduling.annotation.Async("emailTaskExecutor")
    @Override
    public void dispatchEmailAsync(java.util.UUID outboxId) {
        int claimed = emailOutboxRepository.claimForSending(outboxId, LocalDateTime.now(), MAX_SEND_ATTEMPTS);
        if (claimed != 1) {
            log.debug("Email dispatch claim skipped eventId={}; it is already claimed, terminal, or exhausted",
                    outboxId);
            return;
        }
        EmailOutbox outbox = emailOutboxRepository.findById(outboxId).orElse(null);
        if (outbox == null) {
            log.error("Email dispatch claim has no outbox row eventId={}", outboxId);
            return;
        }
        log.info("Email dispatch started eventId={} aggregateId={} retryCount={}",
                outboxId, outbox.getDedupeKey(), outbox.getRetryCount());
        try {
            EmailPayload payload = deserializePayload(outbox);
            boolean sent = emailService.sendHtmlEmail(
                    payload.toEmail(),
                    payload.subject(),
                    payload.htmlBody(),
                    payload.plainTextFallback(),
                    outboxId.toString()
            );
            self.updateOutboxStatus(outboxId, sent ? NotificationStatus.SENT : NotificationStatus.FAILED, sent ? null : "EmailService returned false");
            log.info("Email dispatch finished eventId={} aggregateId={} retryCount={} status={}",
                    outboxId, outbox.getDedupeKey(), outbox.getRetryCount(), sent ? NotificationStatus.SENT : NotificationStatus.FAILED);
        } catch (EmailService.DeliveryOutcomeUnknownException e) {
            log.error("Email dispatch outcome unknown eventId={} aggregateId={} retryCount={} reason={}",
                    outboxId, outbox.getDedupeKey(), outbox.getRetryCount(), e.getMessage(), e);
            // Retrying an SMTP timeout can duplicate a message that the provider accepted.
            // Leave this terminal for manual reconciliation instead of automatic resend.
            self.updateOutboxStatus(outboxId, NotificationStatus.FATAL_ERROR,
                    "Provider outcome unknown: " + e.getMessage());
        } catch (Exception e) {
            log.error("Email dispatch failed eventId={} aggregateId={} retryCount={} reason={}",
                    outboxId, outbox.getDedupeKey(), outbox.getRetryCount(), e.getMessage(), e);
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
            return legacyPayload(outbox, "missing payload_data");
        }
        try {
            JsonNode payloadNode = objectMapper.readTree(outbox.getPayloadData());
            // PostgreSQL JSONB values written through older mappings can be returned as a
            // JSON string containing the actual object. Accept it so queued mail is recoverable.
            if (payloadNode.isTextual()) {
                payloadNode = objectMapper.readTree(payloadNode.textValue());
            }
            if (!payloadNode.isObject()
                    || !payloadNode.hasNonNull("toEmail")
                    || !payloadNode.hasNonNull("subject")
                    || !payloadNode.hasNonNull("htmlBody")) {
                return legacyPayload(outbox, "incomplete payload_data");
            }
            return objectMapper.treeToValue(payloadNode, EmailPayload.class);
        } catch (JsonProcessingException ex) {
            return legacyPayload(outbox, "invalid payload_data");
        }
    }

    private EmailPayload legacyPayload(EmailOutbox outbox, String reason) {
        log.warn("Email outbox {} has {}. Falling back to legacy columns.", outbox.getId(), reason);
        return new EmailPayload(
                outbox.getToEmail(),
                outbox.getSubject(),
                outbox.getBody(),
                outbox.getBody(),
                outbox.getTemplateCode()
        );
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
