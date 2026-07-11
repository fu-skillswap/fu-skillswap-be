package com.fptu.exe.skillswap.modules.mail.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.modules.notification.domain.EmailOutbox;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationStatus;
import com.fptu.exe.skillswap.modules.notification.repository.EmailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailDispatchServiceTest {

    private final EmailService emailService = mock(EmailService.class);
    private final EmailOutboxRepository emailOutboxRepository = mock(EmailOutboxRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private EmailDispatchService service;

    @BeforeEach
    void setUp() {
        service = new EmailDispatchService(emailService, emailOutboxRepository, objectMapper);
        ReflectionTestUtils.setField(service, "self", service);
    }

    @Test
    void sendHtmlOnce_shouldSkipWhenDedupeKeyAlreadyExists() {
        when(emailOutboxRepository.existsByDedupeKey("key-1")).thenReturn(true);

        boolean sent = service.sendHtmlOnce("key-1", "to@test.com", "Subject", "<p>Body</p>", "Body", "TEMPLATE");

        assertFalse(sent);
        verify(emailService, never()).sendHtmlEmail(any(), any(), any(), any());
    }

    @Test
    void sendHtmlOnce_shouldPersistPayloadAndDispatchEmail() throws Exception {
        when(emailOutboxRepository.existsByDedupeKey("key-2")).thenReturn(false);
        when(emailOutboxRepository.saveAndFlush(any(EmailOutbox.class))).thenAnswer(invocation -> {
            EmailOutbox outbox = invocation.getArgument(0);
            outbox.setId(UUID.randomUUID());
            return outbox;
        });
        when(emailOutboxRepository.findById(any(UUID.class))).thenAnswer(invocation -> Optional.of(savedOutbox(invocation.getArgument(0))));
        when(emailService.sendHtmlEmail("to@test.com", "Subject", "<p>Body</p>", "Body")).thenReturn(true);

        boolean accepted = service.sendHtmlOnce("key-2", "to@test.com", "Subject", "<p>Body</p>", "Body", "TEMPLATE");

        assertTrue(accepted);
        verify(emailOutboxRepository).saveAndFlush(any(EmailOutbox.class));
        verify(emailService).sendHtmlEmail("to@test.com", "Subject", "<p>Body</p>", "Body");
        verify(emailOutboxRepository).updateStatus(any(UUID.class), org.mockito.ArgumentMatchers.eq(NotificationStatus.SENT), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void dispatchEmailAsync_shouldDeserializePayloadDataInsteadOfUsingLegacyTemplateField() {
        UUID outboxId = UUID.randomUUID();
        EmailOutbox outbox = EmailOutbox.builder()
                .id(outboxId)
                .toEmail("to@test.com")
                .subject("Subject")
                .body("<p>Body</p>")
                .payloadData("""
                        {"toEmail":"to@test.com","subject":"Subject","htmlBody":"<p>Body</p>","plainTextFallback":"Body","templateCode":"TEMPLATE"}
                        """)
                .templateCode("TEMPLATE")
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .build();
        when(emailOutboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));
        when(emailService.sendHtmlEmail("to@test.com", "Subject", "<p>Body</p>", "Body")).thenReturn(false);

        service.dispatchEmailAsync(outboxId);

        verify(emailService).sendHtmlEmail("to@test.com", "Subject", "<p>Body</p>", "Body");
        verify(emailOutboxRepository).updateStatus(outboxId, NotificationStatus.FAILED, "EmailService returned false");
    }

    private EmailOutbox savedOutbox(UUID id) throws Exception {
        EmailOutbox outbox = EmailOutbox.builder()
                .id(id)
                .toEmail("to@test.com")
                .subject("Subject")
                .body("<p>Body</p>")
                .payloadData(objectMapper.writeValueAsString(new PayloadFixture()))
                .templateCode("TEMPLATE")
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .build();
        JsonNode payload = objectMapper.readTree(outbox.getPayloadData());
        assertTrue(payload.hasNonNull("plainTextFallback"));
        return outbox;
    }

    private record PayloadFixture(
            String toEmail,
            String subject,
            String htmlBody,
            String plainTextFallback,
            String templateCode
    ) {
        private PayloadFixture() {
            this("to@test.com", "Subject", "<p>Body</p>", "Body", "TEMPLATE");
        }
    }
}
