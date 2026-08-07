package com.fptu.exe.skillswap.modules.course.scheduler;

import com.fptu.exe.skillswap.modules.course.service.CourseVaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Webhook delivery is acknowledged first and processed by this retry-safe worker. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BunnyWebhookRetryScheduler {

    private final CourseVaultService courseVaultService;

    @Scheduled(fixedDelay = 5_000)
    public void processPendingWebhookEvents() {
        for (UUID eventId : courseVaultService.claimWebhookEvents(50)) {
            try {
                courseVaultService.processWebhookEventIdempotent(eventId);
            } catch (RuntimeException ex) {
                log.warn("Bunny webhook event {} failed: {}", eventId, ex.getMessage());
                courseVaultService.markWebhookEventFailed(eventId, ex);
            }
        }
    }
}
