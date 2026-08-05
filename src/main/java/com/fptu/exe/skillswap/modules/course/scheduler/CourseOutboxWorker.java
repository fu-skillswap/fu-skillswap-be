package com.fptu.exe.skillswap.modules.course.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.bunny.client.BunnyVideoClient;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterial;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.repository.CourseMaterialRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseOutboxEventRepository;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes;
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
public class CourseOutboxWorker {

    private final CourseOutboxEventRepository outboxEventRepository;
    private final CourseMaterialRepository materialRepository;
    private final BunnyVideoClient bunnyVideoClient;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 10000) // Poll every 10 seconds
    @Transactional
    public void processPendingOutboxEvents() {
        List<UUID> pendingIds = outboxEventRepository.findPendingOrFailedIdsForUpdateSkipLocked(Instant.now(), 50);
        if (pendingIds.isEmpty()) {
            return;
        }

        var events = outboxEventRepository.findAllById(pendingIds);
        for (var event : events) {
            try {
                if (DomainEventOutboxEventTypes.COURSE_MATERIAL_DELETE_REQUESTED.equals(event.getEventType())) {
                    processMaterialDeletion(event.getAggregateId());
                }
                // Mark success
                event.setStatus("PROCESSED");
                event.setLastError(null);
            } catch (Exception e) {
                log.error("Failed to process course outbox event {}", event.getId(), e);
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
            outboxEventRepository.save(event);
        }
    }

    private void processMaterialDeletion(UUID materialId) {
        CourseMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Material not found: " + materialId));

        if (material.getBunnyVideoId() != null && !material.getBunnyVideoId().isBlank()) {
            bunnyVideoClient.deleteVideo(material.getBunnyVideoId());
        }

        material.setStatus(MaterialStatus.DELETED);
        material.setDeletedAt(Instant.now());
        materialRepository.save(material);
    }

    private Instant calculateNextRetryAt(int retryCount) {
        long[] backoffSeconds = {10L, 30L, 60L, 300L}; // 10s, 30s, 1m, 5m
        long seconds = backoffSeconds[Math.min(Math.max(retryCount - 1, 0), backoffSeconds.length - 1)];
        return Instant.now().plus(seconds, ChronoUnit.SECONDS);
    }
}
