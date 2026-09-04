package com.fptu.exe.skillswap.modules.notification.service;

import com.fptu.exe.skillswap.modules.notification.domain.EmailOutbox;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationStatus;
import com.fptu.exe.skillswap.modules.notification.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "application.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class EmailRetryScheduler {

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailDispatchService emailDispatchService;

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void retryFailedEmails() {
        String threadName = Thread.currentThread().getName();
        log.info("[{}] Bắt đầu tiến trình retry gửi email FAILED...", threadName);
        int fatalCount = emailOutboxRepository.updateFailedToFatalError(EmailDispatchService.MAX_SEND_ATTEMPTS);
        if (fatalCount > 0) {
            log.warn("[{}] Đã chuyển {} email FAILED sang FATAL_ERROR do vượt quá số lần retry.", threadName, fatalCount);
        }

        int uncertainCount = emailOutboxRepository.quarantineStaleSending(LocalDateTime.now().minusMinutes(15));
        if (uncertainCount > 0) {
            log.error("[{}] Đã chuyển {} email SENDING stale sang FATAL_ERROR; delivery outcome is unknown and requires manual review.",
                    threadName, uncertainCount);
        }
        
        List<EmailOutbox> pendingEmails = emailOutboxRepository.findBatchByStatusForUpdate(
                NotificationStatus.PENDING,
                PageRequest.of(0, 20)
        );
        for (EmailOutbox outbox : pendingEmails) {
            emailDispatchService.dispatchEmailAsync(outbox.getId());
        }

        List<EmailOutbox> failedEmails = emailOutboxRepository.findRetryBatchForUpdate(
                NotificationStatus.FAILED,
                EmailDispatchService.MAX_SEND_ATTEMPTS,
                PageRequest.of(0, 10)
        );
        if (failedEmails.isEmpty()) {
            log.debug("[{}] Không có email FAILED nào cần retry.", threadName);
            return;
        }

        log.info("[{}] Tìm thấy {} email FAILED cần retry.", threadName, failedEmails.size());
        for (EmailOutbox outbox : failedEmails) {
            log.info("[{}] Đang retry gửi email ID: {}", threadName, outbox.getId());
            // Reset to PENDING in this transaction; the worker atomically claims it as
            // SENDING before invoking the provider.
            emailOutboxRepository.updateStatus(outbox.getId(), NotificationStatus.PENDING, null);
            emailDispatchService.dispatchEmailAsync(outbox.getId());
        }
    }
}
