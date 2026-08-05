package com.fptu.exe.skillswap.modules.course.scheduler;

import com.fptu.exe.skillswap.infrastructure.bunny.client.BunnyVideoClient;
import com.fptu.exe.skillswap.modules.course.domain.CourseMaterial;
import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.domain.StorageProviderType;
import com.fptu.exe.skillswap.modules.course.repository.CourseMaterialRepository;
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

    private final CourseMaterialRepository materialRepository;
    private final com.fptu.exe.skillswap.modules.course.repository.CourseOutboxEventRepository outboxEventRepository;

    // Run every hour
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredUploads() {
        log.info("Starting cleanup of expired course material uploads");
        
        // Find UPLOADING materials older than 24 hours
        Instant cutoff = Instant.now().minusSeconds(24 * 3600);
        List<CourseMaterial> expiredMaterials = materialRepository.findByStatusAndUploadedAtBefore(MaterialStatus.UPLOADING, cutoff);
        
        for (CourseMaterial material : expiredMaterials) {
            log.info("Expiring material upload ID: {}", material.getId());
            material.setStatus(MaterialStatus.EXPIRED);
            materialRepository.save(material);
            
            // If it was a Bunny.net video, use the outbox to delete the placeholder video GUID
            if (material.getStorageProviderType() == StorageProviderType.BUNNY_VIDEO && material.getBunnyVideoId() != null) {
                com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent outboxEvent = com.fptu.exe.skillswap.modules.course.domain.CourseOutboxEvent.builder()
                        .aggregateType("CourseMaterial")
                        .aggregateId(material.getId())
                        .eventType(com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes.COURSE_MATERIAL_DELETE_REQUESTED)
                        .payloadJson("{}")
                        .status("PENDING")
                        .build();
                outboxEventRepository.save(outboxEvent);
                log.info("Scheduled deletion of expired video GUID {} from Bunny.net", material.getBunnyVideoId());
            }
        }
        
        log.info("Finished cleanup of expired course material uploads. Processed {} items.", expiredMaterials.size());
    }
}
