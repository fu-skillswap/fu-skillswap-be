package com.fptu.exe.skillswap.modules.notification.port;

import java.util.UUID;

/** Public email-outbox capability for modules that publish email notifications. */
public interface EmailDispatchPort {
    boolean sendHtmlOnce(
            String dedupeKey,
            String toEmail,
            String subject,
            String htmlBody,
            String plainTextFallback,
            String templateCode
    );

    UUID queueHtmlOnce(
            String dedupeKey,
            String toEmail,
            String subject,
            String htmlBody,
            String plainTextFallback,
            String templateCode
    );

    void dispatchEmailAsync(UUID outboxId);
}
