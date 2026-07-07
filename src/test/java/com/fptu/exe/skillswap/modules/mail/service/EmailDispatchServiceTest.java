package com.fptu.exe.skillswap.modules.mail.service;

import com.fptu.exe.skillswap.modules.notification.domain.EmailOutbox;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationStatus;
import com.fptu.exe.skillswap.modules.notification.repository.EmailOutboxRepository;
import org.junit.jupiter.api.Test;

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
    private final EmailDispatchService service = new EmailDispatchService(emailService, emailOutboxRepository);

    @Test
    void sendHtmlOnce_shouldSkipWhenDedupeKeyAlreadyExists() {
        when(emailOutboxRepository.existsByDedupeKey("key-1")).thenReturn(true);

        boolean sent = service.sendHtmlOnce("key-1", "to@test.com", "Subject", "<p>Body</p>", "Body", "TEMPLATE");

        assertFalse(sent);
        verify(emailService, never()).sendHtmlEmail(any(), any(), any(), any());
    }

    @Test
    void sendHtmlOnce_shouldPersistSentOutboxWhenEmailSent() {
        when(emailOutboxRepository.existsByDedupeKey("key-2")).thenReturn(false);
        when(emailOutboxRepository.saveAndFlush(any(EmailOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailOutboxRepository.save(any(EmailOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailService.sendHtmlEmail("to@test.com", "Subject", "<p>Body</p>", "Body")).thenReturn(true);

        boolean sent = service.sendHtmlOnce("key-2", "to@test.com", "Subject", "<p>Body</p>", "Body", "TEMPLATE");

        assertTrue(sent);
        verify(emailOutboxRepository).save(org.mockito.ArgumentMatchers.argThat(outbox ->
                outbox.getStatus() == NotificationStatus.SENT
                        && outbox.getSentAt() != null
                        && outbox.getLastError() == null
        ));
    }

    @Test
    void sendHtmlOnce_shouldPersistFailedOutboxWhenEmailFails() {
        when(emailOutboxRepository.existsByDedupeKey("key-3")).thenReturn(false);
        when(emailOutboxRepository.saveAndFlush(any(EmailOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailOutboxRepository.save(any(EmailOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailService.sendHtmlEmail("to@test.com", "Subject", "<p>Body</p>", "Body")).thenReturn(false);

        boolean sent = service.sendHtmlOnce("key-3", "to@test.com", "Subject", "<p>Body</p>", "Body", "TEMPLATE");

        assertFalse(sent);
        verify(emailOutboxRepository).save(org.mockito.ArgumentMatchers.argThat(outbox ->
                outbox.getStatus() == NotificationStatus.FAILED
                        && outbox.getSentAt() == null
                        && outbox.getLastError() != null
        ));
    }
}
