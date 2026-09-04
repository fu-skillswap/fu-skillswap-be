package com.fptu.exe.skillswap.modules.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.net.SocketTimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String MAIL_ENCODING = StandardCharsets.UTF_8.name();

    private final JavaMailSender javaMailSender;

    @Value("${application.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${application.mail.from:no-reply@skillswap.asia}")
    private String senderEmail;

    @Value("${application.mail.from-name:SkillSwap}")
    private String senderName;

    public boolean sendSimpleEmail(String to, String subject, String body) {
        if (!mailEnabled) {
            log.info("Email service is disabled. Skipping email to: {}, subject: {}", to, subject);
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (org.springframework.util.StringUtils.hasText(senderName)) {
                message.setFrom(senderName + " <" + senderEmail + ">");
            } else {
                message.setFrom(senderEmail);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            javaMailSender.send(message);
            log.info("Email sent successfully to: {}", to);
            return true;
        } catch (Exception ex) {
            log.error("Failed to send email to: {}. Reason: {}", to, ex.getMessage(), ex);
            // We specifically DO NOT throw the exception to prevent rolling back business transactions
            return false;
        }
    }

    public boolean sendHtmlEmail(String to, String subject, String htmlBody, String plainTextFallback) {
        return sendHtmlEmail(to, subject, htmlBody, plainTextFallback, null);
    }

    /**
     * Sends with a stable per-outbox identity. SMTP itself cannot provide a transactional
     * idempotency guarantee, but the stable Message-ID/X-Idempotency-Key lets providers and
     * receiving systems collapse retries when supported and prevents a new identity being
     * generated for the same durable outbox row.
     */
    public boolean sendHtmlEmail(String to, String subject, String htmlBody, String plainTextFallback,
                                 String idempotencyKey) {
        if (!mailEnabled) {
            log.info("Email service is disabled. Skipping HTML email to: {}, subject: {}", to, subject);
            return false;
        }

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    MAIL_ENCODING
            );
            if (org.springframework.util.StringUtils.hasText(senderName)) {
                helper.setFrom(senderEmail, senderName);
            } else {
                helper.setFrom(senderEmail);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainTextFallback == null ? "" : plainTextFallback, htmlBody);
            if (org.springframework.util.StringUtils.hasText(idempotencyKey)) {
                message.setHeader("Message-ID", "<email-outbox-" + idempotencyKey + "@skillswap.asia>");
                message.setHeader("X-Idempotency-Key", idempotencyKey);
            }
            message.setHeader("Content-Language", "vi");
            message.setHeader("Content-Transfer-Encoding", "8bit");

            javaMailSender.send(message);
            log.info("HTML email sent successfully to: {}", to);
            return true;
        } catch (Exception ex) {
            if (isDeliveryOutcomeUnknown(ex)) {
                throw new DeliveryOutcomeUnknownException("SMTP provider outcome is unknown", ex);
            }
            log.error("Failed to send HTML email to: {}. Reason: {}", to, ex.getMessage(), ex);
            // Email is best-effort and must never roll back business transactions.
            return false;
        }
    }

    private boolean isDeliveryOutcomeUnknown(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof IOException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).matches(".*(timeout|timed out|connection reset|broken pipe).*")) {
                return true;
            }
        }
        return false;
    }

    public static class DeliveryOutcomeUnknownException extends RuntimeException {
        public DeliveryOutcomeUnknownException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
