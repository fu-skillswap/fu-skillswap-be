package com.fptu.exe.skillswap.modules.course.scheduler;

import com.fptu.exe.skillswap.modules.course.service.CourseVaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Claims local work in short transactions; Bunny calls are deliberately outside them. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "application.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CourseOutboxWorker {

    private final CourseVaultService courseVaultService;

    @Scheduled(fixedDelay = 10_000)
    public void processPendingOutboxEvents() {
        for (UUID eventId : courseVaultService.claimCourseOutboxEvents(50)) {
            try {
                courseVaultService.processCourseOutboxEvent(eventId);
            } catch (RuntimeException ex) {
                log.warn("Course outbox event {} failed: {}", eventId, ex.getMessage());
                courseVaultService.markCourseOutboxEventFailed(eventId, ex);
            }
        }
    }
}
