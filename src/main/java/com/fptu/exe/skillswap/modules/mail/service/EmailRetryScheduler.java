package com.fptu.exe.skillswap.modules.mail.service;

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

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailRetryScheduler {

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailDispatchService emailDispatchService;

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void retryFailedEmails() {
        String threadName = Thread.currentThread().getName();
        log.info("[{}] Bắt đầu tiến trình retry gửi email FAILED...", threadName);
        int fatalCount = emailOutboxRepository.updateFailedToFatalError(3);
        if (fatalCount > 0) {
            log.warn("[{}] Đã chuyển {} email FAILED sang FATAL_ERROR do vượt quá số lần retry.", threadName, fatalCount);
        }
        
        List<EmailOutbox> failedEmails = emailOutboxRepository.findRetryBatchForUpdate(
                NotificationStatus.FAILED,
                3,
                PageRequest.of(0, 10)
        );
        if (failedEmails.isEmpty()) {
            log.info("[{}] Không có email FAILED nào cần retry.", threadName);
            return;
        }

        log.info("[{}] Tìm thấy {} email FAILED cần retry.", threadName, failedEmails.size());
        for (EmailOutbox outbox : failedEmails) {
            log.info("[{}] Đang retry gửi email ID: {}", threadName, outbox.getId());
            emailOutboxRepository.updateStatus(outbox.getId(), NotificationStatus.PENDING, null);
            emailDispatchService.dispatchEmailAsync(outbox.getId());
        }
    }
}
