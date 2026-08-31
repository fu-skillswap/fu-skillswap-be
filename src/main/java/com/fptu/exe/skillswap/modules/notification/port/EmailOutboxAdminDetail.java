package com.fptu.exe.skillswap.modules.notification.port;

import java.time.LocalDateTime;
import java.util.UUID;

public record EmailOutboxAdminDetail(UUID emailOutboxId, String toEmail, String subject, String templateCode,
                                     String status, Integer retryCount, LocalDateTime createdAt,
                                     LocalDateTime sentAt, String body, String lastError) { }
