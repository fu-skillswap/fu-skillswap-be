package com.fptu.exe.skillswap.modules.notification.port;

import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.UUID;
import java.util.List;

/** Admin operations over the notification outbox without exposing its JPA aggregate. */
public interface EmailOutboxAdminPort {
    boolean existsById(UUID emailOutboxId);
    PageResponse<EmailOutboxAdminSummary> search(EmailOutboxAdminQuery query);
    EmailOutboxAdminDetail get(UUID emailOutboxId);
    EmailOutboxRetryResult retryFailed(UUID emailOutboxId);
    List<String> statusNames();
}
