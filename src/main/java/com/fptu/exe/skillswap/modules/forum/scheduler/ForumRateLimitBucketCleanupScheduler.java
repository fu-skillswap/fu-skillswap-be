package com.fptu.exe.skillswap.modules.forum.scheduler;

import com.fptu.exe.skillswap.infrastructure.storage.StorageLifecycleProperties;
import com.fptu.exe.skillswap.modules.forum.service.ForumRateLimitBucketCleanupService;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Periodically removes old rate-limit buckets without blocking active requests. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "application.storage.lifecycle", name = "cleanup-enabled", havingValue = "true")
public class ForumRateLimitBucketCleanupScheduler {

    private static final int MAX_BATCHES_PER_RUN = 100;

    private final ForumRateLimitBucketCleanupService cleanupService;
    private final StorageLifecycleProperties properties;
    private final TimeProvider timeProvider;

    @Scheduled(
            cron = "${application.storage.lifecycle.forum-rate-limit-cleanup-cron:0 */10 * * * *}",
            zone = "UTC"
    )
    public int executeCleanupJob() {
        int retentionDays = properties.getForumRateLimitRetentionDays();
        if (retentionDays <= 0) {
            log.info("Forum rate-limit cleanup is disabled because retentionDays <= 0");
            return 0;
        }

        int batchSize = Math.max(50, properties.getCleanupBatchSize());
        LocalDateTime cutoff = timeProvider.localDateTime(TimeProvider.UTC_ZONE).minusDays(retentionDays);
        int totalDeleted = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = cleanupService.deleteSingleExpiredBatch(cutoff, batchSize);
            totalDeleted += deleted;
            if (deleted < batchSize) {
                break;
            }
        }

        log.info("Forum rate-limit bucket cleanup completed. deleted={}, retentionDays={}, batchSize={}",
                totalDeleted, retentionDays, batchSize);
        return totalDeleted;
    }
}
