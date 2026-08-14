package com.fptu.exe.skillswap.modules.identity.scheduler;

import com.fptu.exe.skillswap.infrastructure.storage.StorageLifecycleProperties;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "application.storage.lifecycle", name = "cleanup-enabled", havingValue = "true")
public class UserSessionCleanupScheduler {

    private final UserSessionRepository userSessionRepository;
    private final StorageLifecycleProperties properties;

    @Scheduled(cron = "${application.storage.lifecycle.user-session-cleanup-cron:0 5 2 * * *}")
    @Transactional
    public void cleanupExpiredOrRevokedSessions() {
        int retentionDays = properties.getUserSessionRetentionDays();
        if (retentionDays <= 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(retentionDays);
        int totalDeleted = 0;
        int batchSize = Math.max(50, properties.getCleanupBatchSize());

        for (int batch = 0; batch < 100; batch++) {
            int deleted = userSessionRepository.deleteExpiredOrRevokedBatch(cutoff, cutoff, batchSize);
            totalDeleted += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        log.info("User session cleanup completed. deleted={}, retentionDays={}", totalDeleted, retentionDays);
    }
}
