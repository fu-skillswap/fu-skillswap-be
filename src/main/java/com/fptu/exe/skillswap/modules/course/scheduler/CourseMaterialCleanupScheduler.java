package com.fptu.exe.skillswap.modules.course.scheduler;

import com.fptu.exe.skillswap.modules.course.domain.MaterialStatus;
import com.fptu.exe.skillswap.modules.course.repository.CourseMaterialRepository;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class CourseMaterialCleanupScheduler {
    private final CourseMaterialRepository materialRepository;
    private final TimeProvider timeProvider;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void cleanupExpiredUploads() {
        var expired = materialRepository.findByStatusAndUploadExpiresAtBefore(MaterialStatus.UPLOADING, timeProvider.instant());
        expired.forEach(material -> material.setStatus(MaterialStatus.EXPIRED));
        if (!expired.isEmpty()) log.info("Expired {} unfinished course material uploads", expired.size());
    }
}
