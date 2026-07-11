package com.fptu.exe.skillswap.modules.mail.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @InjectMocks
    private EmailService emailService;

    @Test
    void sendSimpleEmail_shouldSendEmail_whenEnabled() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "senderEmail", "no-reply@skillswap.com");

        emailService.sendSimpleEmail("test@test.com", "Subject", "Body");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertEquals("no-reply@skillswap.com", sentMessage.getFrom());
        assertEquals("Subject", sentMessage.getSubject());
        assertEquals("Body", sentMessage.getText());
        assertEquals(1, sentMessage.getTo().length);
        assertEquals("test@test.com", sentMessage.getTo()[0]);
    }

    @Test
    void sendSimpleEmail_shouldSkip_whenDisabled() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);

        emailService.sendSimpleEmail("test@test.com", "Subject", "Body");

        verify(javaMailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendSimpleEmail_shouldCatchException_whenMailSenderThrows() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "senderEmail", "no-reply@skillswap.com");

        doThrow(new MailSendException("SMTP timeout")).when(javaMailSender).send(any(SimpleMailMessage.class));

        // Ensure that exception is caught and does not crash the calling thread
        assertDoesNotThrow(() -> emailService.sendSimpleEmail("test@test.com", "Subject", "Body"));
    }

    @Test
    void sendHtmlEmail_shouldSendMimeMessage_whenEnabled() throws Exception {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "senderEmail", "no-reply@skillswap.com");
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendHtmlEmail("test@test.com", "[SkillSwap] Mentor đã chấp nhận lịch", "<h1>Xin chào</h1>", "Xin chào");

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, times(1)).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();
        assertEquals("[SkillSwap] Mentor đã chấp nhận lịch", sentMessage.getSubject());
        assertEquals("no-reply@skillswap.com", sentMessage.getFrom()[0].toString());
        assertEquals("test@test.com", sentMessage.getAllRecipients()[0].toString());
        assertEquals("vi", sentMessage.getHeader("Content-Language", null));
        assertEquals("8bit", sentMessage.getHeader("Content-Transfer-Encoding", null));
    }
}
