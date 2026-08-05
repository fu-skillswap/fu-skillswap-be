package com.fptu.exe.skillswap.modules.course.scheduler;

import com.fptu.exe.skillswap.modules.course.domain.BunnyWebhookEvent;
import com.fptu.exe.skillswap.modules.course.repository.BunnyWebhookEventRepository;
import com.fptu.exe.skillswap.modules.course.service.CourseVaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BunnyWebhookRetryScheduler {

    private final BunnyWebhookEventRepository webhookEventRepository;
    private final CourseVaultService courseVaultService;

    @Scheduled(fixedDelay = 5000) // Poll every 5 seconds
    @Transactional
    public void processPendingWebhookEvents() {
        List<UUID> pendingIds = webhookEventRepository.findPendingOrFailedIdsForUpdateSkipLocked(Instant.now(), 50);
        if (pendingIds.isEmpty()) {
            return;
        }

        var events = webhookEventRepository.findAllById(pendingIds);
        for (var event : events) {
            try {
                courseVaultService.processWebhookEventIdempotent(event);
                
                event.setStatus("PROCESSED");
                event.setLastError(null);
            } catch (Exception e) {
                log.error("Failed to process bunny webhook event {}", event.getId(), e);
                int nextAttempt = event.getRetryCount() + 1;
                event.setRetryCount(nextAttempt);
                event.setLastError(e.getMessage() != null && e.getMessage().length() > 500 ? e.getMessage().substring(0, 500) : e.getMessage());
                if (nextAttempt >= 5) {
                    event.setStatus("DEAD_LETTER");
                } else {
                    event.setStatus("FAILED");
                    event.setNextRetryAt(calculateNextRetryAt(nextAttempt));
                }
            }
            webhookEventRepository.save(event);
        }
    }

    private Instant calculateNextRetryAt(int retryCount) {
        long[] backoffSeconds = {10L, 30L, 60L, 300L}; // 10s, 30s, 1m, 5m
        long seconds = backoffSeconds[Math.min(Math.max(retryCount - 1, 0), backoffSeconds.length - 1)];
        return Instant.now().plus(seconds, ChronoUnit.SECONDS);
    }
}
