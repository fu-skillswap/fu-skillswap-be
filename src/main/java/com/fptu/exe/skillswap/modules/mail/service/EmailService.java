package com.fptu.exe.skillswap.modules.mail.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${application.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${application.mail.from:no-reply@skillswap.asia}")
    private String senderEmail;

    public void sendSimpleEmail(String to, String subject, String body) {
        if (!mailEnabled) {
            log.info("Email service is disabled. Skipping email to: {}, subject: {}", to, subject);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            javaMailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (Exception ex) {
            log.error("Failed to send email to: {}. Reason: {}", to, ex.getMessage(), ex);
            // We specifically DO NOT throw the exception to prevent rolling back business transactions
        }
    }

    public void sendHtmlEmail(String to, String subject, String htmlBody, String plainTextFallback) {
        if (!mailEnabled) {
            log.info("Email service is disabled. Skipping HTML email to: {}, subject: {}", to, subject);
            return;
        }

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainTextFallback == null ? "" : plainTextFallback, htmlBody);

            javaMailSender.send(message);
            log.info("HTML email sent successfully to: {}", to);
        } catch (Exception ex) {
            log.error("Failed to send HTML email to: {}. Reason: {}", to, ex.getMessage(), ex);
            // Email is best-effort and must never roll back business transactions.
        }
    }
}
