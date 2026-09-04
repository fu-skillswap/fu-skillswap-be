package com.fptu.exe.skillswap.modules.course.scheduler;

import com.fptu.exe.skillswap.infrastructure.storage.StorageLifecycleProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "application.storage.lifecycle", name = "cleanup-enabled", havingValue = "true")
public class CourseOutboxCleanupScheduler {

    private final JdbcTemplate jdbcTemplate;
    private final StorageLifecycleProperties properties;

    @Scheduled(cron = "${application.storage.lifecycle.course-outbox-cleanup-cron:0 30 2 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void executeCleanupJob() {
        int retentionDays = properties.getCourseOutboxRetentionDays();
        if (retentionDays <= 0) {
            log.info("CourseOutbox cleanup is disabled because retentionDays <= 0");
            return;
        }

        log.info("Starting CourseOutbox cleanup job for records older than {} days", retentionDays);
        long startTime = System.currentTimeMillis();
        int totalDeleted = 0;
        int maxBatches = 100;
        int batchSize = 500;

        String sql = "DELETE FROM course_outbox_events WHERE id IN ( " +
                "SELECT id FROM course_outbox_events " +
                "WHERE status IN ('SUCCEEDED', 'FAILED') " +
                "AND created_at < NOW() - CAST(? AS INTERVAL) " +
                "LIMIT ? " +
                ")";

        for (int i = 0; i < maxBatches; i++) {
            int deleted = jdbcTemplate.update(sql, retentionDays + " days", batchSize);
            totalDeleted += deleted;
            if (deleted < batchSize) {
                break;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Finished CourseOutbox cleanup job. Deleted {} records in {} ms", totalDeleted, duration);
    }
}
