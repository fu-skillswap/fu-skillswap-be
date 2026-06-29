package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.mail.service.EmailService;
import com.fptu.exe.skillswap.modules.mentor.event.MentorVerificationEmailListener;
import com.fptu.exe.skillswap.modules.mentor.event.MentorVerificationEmailNotificationEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MentorVerificationEmailListenerTest {

    @Test
    void handleApprovedEmail_shouldSendHtmlEmail() {
        EmailService emailService = mock(EmailService.class);
        MentorVerificationEmailListener listener = new MentorVerificationEmailListener(emailService);

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

        verify(emailService).sendHtmlEmail(
                eq("mentor@test.com"),
                eq("[SkillSwap] Hồ sơ mentor đã được duyệt"),
                contains("Hồ sơ mentor đã được duyệt"),
                contains("Hồ sơ mentor đã được duyệt")
        );
    }

    @Test
    void handleNeedsRevisionEmail_shouldSendHtmlEmailWithReviewNote() {
        EmailService emailService = mock(EmailService.class);
        MentorVerificationEmailListener listener = new MentorVerificationEmailListener(emailService);

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

        verify(emailService).sendHtmlEmail(
                eq("mentor@test.com"),
                eq("[SkillSwap] Cần bổ sung hồ sơ mentor"),
                contains("Cần bổ sung minh chứng chuyên môn"),
                contains("Cần bổ sung minh chứng chuyên môn")
        );
    }
}
