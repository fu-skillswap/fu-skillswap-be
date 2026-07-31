package com.fptu.exe.skillswap.infrastructure.realtime;

import com.fptu.exe.skillswap.infrastructure.config.RealtimeOutboxProperties;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxBatchCleanupService;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "application.realtime.outbox", name = "cleanup-enabled", havingValue = "true")
public class DomainEventOutboxCleanupScheduler {

    private final DomainEventOutboxBatchCleanupService batchCleanupService;
    private final RealtimeOutboxProperties properties;

    @Scheduled(cron = "${application.realtime.outbox.cleanup-cron:0 0 2 * * *}")
    public int executeCleanupJob() {
        long startTimeMs = System.currentTimeMillis();
        LocalDateTime publishedBefore = DateTimeUtil.now().minusDays(properties.getRetentionDays());
        LocalDateTime failedBefore = DateTimeUtil.now().minusDays(properties.getRetentionDays());

        int totalDeleted = 0;
        int batchesProcessed = 0;
        boolean limitReached = false;

        log.info("Starting outbox cleanup job. retentionDays={}, batchSize={}, maxBatches={}",
                properties.getRetentionDays(), properties.getCleanupBatchSize(), properties.getMaxBatchesPerRun());

        while (batchesProcessed < properties.getMaxBatchesPerRun()) {
            long elapsedSeconds = (System.currentTimeMillis() - startTimeMs) / 1000;
            if (elapsedSeconds >= properties.getMaxExecutionSeconds()) {
                limitReached = true;
                break;
            }

            int deletedPublished = batchCleanupService.deleteSinglePublishedBatch(publishedBefore, properties.getCleanupBatchSize());
            int deletedFailed = batchCleanupService.deleteSingleFailedBatch(failedBefore, properties.getMaxRetryAttempts(), properties.getCleanupBatchSize());
            int batchTotal = deletedPublished + deletedFailed;

            if (batchTotal == 0) {
                break;
            }

            totalDeleted += batchTotal;
            batchesProcessed++;
        }

        long durationMs = System.currentTimeMillis() - startTimeMs;

        if (limitReached || batchesProcessed >= properties.getMaxBatchesPerRun()) {
            log.warn("metric_name=outbox_cleanup_backlog_exceeded_total status=LIMIT_REACHED Batches processed={}, Total deleted={}, DurationMs={}",
                    batchesProcessed, totalDeleted, durationMs);
        } else {
            log.info("Outbox cleanup job completed cleanly. Batches processed={}, Total deleted={}, DurationMs={}",
                    batchesProcessed, totalDeleted, durationMs);
        }

        return totalDeleted;
    }
}
