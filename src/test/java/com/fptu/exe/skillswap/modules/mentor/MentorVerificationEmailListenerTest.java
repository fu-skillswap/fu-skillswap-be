package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.mail.service.EmailDispatchService;
import com.fptu.exe.skillswap.modules.mentor.event.MentorVerificationEmailListener;
import com.fptu.exe.skillswap.modules.mentor.event.MentorVerificationEmailNotificationEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MentorVerificationEmailListenerTest {

    @Test
    void handleApprovedEmail_shouldQueueDispatch() {
        EmailDispatchService emailDispatchService = mock(EmailDispatchService.class);
        when(emailDispatchService.sendHtmlOnce(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        MentorVerificationEmailListener listener = new MentorVerificationEmailListener(emailDispatchService);

        listener.handleMentorVerificationEmail(MentorVerificationEmailNotificationEvent.builder()
                .eventType(MentorVerificationEmailNotificationEvent.EventType.APPROVED_EMAIL)
                .requestId(UUID.randomUUID())
                .recipientEmail("mentor@test.com")
                .recipientName("Mentor Test")
                .reviewerName("Admin Test")
                .reviewNote("Hồ sơ tốt")
                .submittedAt(LocalDateTime.of(2026, 6, 29, 9, 0))
                .reviewedAt(LocalDateTime.of(2026, 6, 29, 10, 0))
                .build());

        verify(emailDispatchService).sendHtmlOnce(
                org.mockito.ArgumentMatchers.startsWith("MENTOR_VERIFICATION_EMAIL:APPROVED_EMAIL:"),
                eq("mentor@test.com"),
                eq("[SkillSwap] Hồ sơ mentor của bạn đã được duyệt"),
                org.mockito.ArgumentMatchers.contains("'Segoe UI', Arial, Helvetica, sans-serif"),
                org.mockito.ArgumentMatchers.contains("Hồ sơ mentor của bạn đã được duyệt"),
                eq("APPROVED_EMAIL")
        );
    }

    @Test
    void handleNeedsRevisionEmail_shouldQueueDispatchWithReviewNote() {
        EmailDispatchService emailDispatchService = mock(EmailDispatchService.class);
        when(emailDispatchService.sendHtmlOnce(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        MentorVerificationEmailListener listener = new MentorVerificationEmailListener(emailDispatchService);

        listener.handleMentorVerificationEmail(MentorVerificationEmailNotificationEvent.builder()
                .eventType(MentorVerificationEmailNotificationEvent.EventType.NEEDS_REVISION_EMAIL)
                .requestId(UUID.randomUUID())
                .recipientEmail("mentor@test.com")
                .recipientName("Mentor Test")
                .reviewerName("Admin Test")
                .reviewNote("Cần bổ sung minh chứng chuyên môn")
                .submittedAt(LocalDateTime.of(2026, 6, 29, 9, 0))
                .reviewedAt(LocalDateTime.of(2026, 6, 29, 10, 0))
                .build());

        verify(emailDispatchService).sendHtmlOnce(
                org.mockito.ArgumentMatchers.startsWith("MENTOR_VERIFICATION_EMAIL:NEEDS_REVISION_EMAIL:"),
                eq("mentor@test.com"),
                eq("[SkillSwap] Cần bổ sung hồ sơ mentor"),
                org.mockito.ArgumentMatchers.contains("'Segoe UI', Arial, Helvetica, sans-serif"),
                org.mockito.ArgumentMatchers.contains("Cần bổ sung minh chứng chuyên môn"),
                eq("NEEDS_REVISION_EMAIL")
        );
    }
}
