package com.fptu.exe.skillswap.modules.course.scheduler;

import com.fptu.exe.skillswap.modules.course.domain.LectureResource;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.repository.LectureResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CourseMaterialCleanupScheduler {

    private final LectureResourceRepository resourceRepository;
    private final com.fptu.exe.skillswap.modules.course.repository.CourseOutboxEventRepository outboxEventRepository;

    // Run every hour
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredUploads() {
        log.info("Starting cleanup of expired lecture resource uploads");
        
        Instant cutoff = Instant.now().minusSeconds(24 * 3600);
        List<LectureResource> expiredResources = resourceRepository.findAll().stream()
                .filter(r -> r.getStatus() == MaterialStatus.UPLOADING && r.getUploadedAt().isBefore(cutoff))
                .toList();
        
        for (LectureResource resource : expiredResources) {
            log.info("Expiring resource upload ID: {}", resource.getId());
            resource.setStatus(MaterialStatus.EXPIRED);
            resourceRepository.save(resource);

            if (resource.getStorageProviderType() == StorageProviderType.BUNNY_VIDEO && resource.getBunnyVideoId() != null) {
                com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent outboxEvent = com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent.builder()
                        .aggregateType("LectureResource")
                        .aggregateId(resource.getId())
                        .eventType(com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.COURSE_MATERIAL_DELETE_REQUESTED)
                        .payloadJson("{}")
                        .status("PENDING")
                        .build();
                outboxEventRepository.save(outboxEvent);
                log.info("Scheduled deletion of expired video GUID {} from Bunny.net", resource.getBunnyVideoId());
            }
        }
        
        log.info("Finished cleanup of expired lecture resource uploads. Processed {} items.", expiredResources.size());
    }
}
