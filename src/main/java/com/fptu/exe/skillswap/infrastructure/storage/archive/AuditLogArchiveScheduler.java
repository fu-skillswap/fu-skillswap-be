package com.fptu.exe.skillswap.infrastructure.storage.archive;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.storage.StorageLifecycleProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "application.storage.lifecycle", name = "archive-enabled", havingValue = "true")
public class AuditLogArchiveScheduler {

    private final JdbcTemplate jdbcTemplate;
    private final StorageArchiveService storageArchiveService;
    private final StorageLifecycleProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Scheduled(cron = "${application.storage.lifecycle.audit-log-archive-cron:0 0 4 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void executeArchiveJob() {
        int retentionDays = properties.getAuditLogRetentionDays();
        if (retentionDays <= 0) {
            log.info("AuditLog archive is disabled (retentionDays <= 0)");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("AuditLog archive job is already running, skipping this execution.");
            return;
        }

        try {
            log.info("Starting AuditLog archive job for records older than {} days", retentionDays);
            long startTime = System.currentTimeMillis();
            int batchSize = 1000;
            int maxBatches = 50;
            int totalArchived = 0;

            String selectSql = "SELECT id, created_at, actor_user_id, entity_type, entity_id, action, old_value, new_value, ip_address, user_agent " +
                    "FROM audit_logs " +
                    "WHERE created_at < NOW() - CAST(? AS INTERVAL) " +
                    "ORDER BY created_at ASC, id ASC LIMIT ?";

            String deleteSql = "DELETE FROM audit_logs WHERE id = ?";

            for (int i = 0; i < maxBatches; i++) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql, retentionDays + " days", batchSize);
                if (rows.isEmpty()) {
                    break;
                }

                List<String> jsonLines = new ArrayList<>();
                List<UUID> idsToDelete = new ArrayList<>();

                for (Map<String, Object> row : rows) {
                    UUID id = readUuid(row.get("id"));
                    if (id == null) {
                        log.error("Skipping audit log archive row with invalid id={}", row.get("id"));
                        continue;
                    }

                    try {
                        jsonLines.add(objectMapper.writeValueAsString(row));
                        idsToDelete.add(id);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize audit log row id={}", id, e);
                    }
                }

                if (!jsonLines.isEmpty()) {
                    LocalDateTime now = com.fptu.exe.skillswap.shared.util.DateTimeUtil.now();
                    String prefix = String.format("archives/audit_logs/%04d/%02d/%02d",
                            now.getYear(), now.getMonthValue(), now.getDayOfMonth());

                    try {
                        String identity = idsToDelete.get(0) + "-" + idsToDelete.get(idsToDelete.size() - 1);
                        StorageArchiveService.ArchiveBatchResult result = storageArchiveService.archiveJsonLines(prefix, identity, jsonLines);
                        if (result == null) {
                            log.warn("Archiving returned null result, skipping deletion for this batch.");
                            break;
                        }

                        List<Object[]> batchArgs = idsToDelete.stream()
                                .map(id -> new Object[]{id})
                                .collect(Collectors.toList());
                        int[] updateCounts = jdbcTemplate.batchUpdate(deleteSql, batchArgs);
                        int deletedCount = 0;
                        for (int count : updateCounts) {
                            deletedCount += count > 0 ? count : count == java.sql.Statement.SUCCESS_NO_INFO ? 1 : 0;
                        }

                        if (deletedCount != idsToDelete.size()) {
                            log.error("Integrity error: Deleted {} rows but archived {}. Halting batch to prevent data inconsistency.", deletedCount, idsToDelete.size());
                            break;
                        }
                        totalArchived += deletedCount;
                    } catch (Exception ex) {
                        log.error("Archive batch failed. Aborting batch delete to prevent data loss.", ex);
                        break;
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Finished AuditLog archive job. Archived {} records in {} ms", totalArchived, duration);
        } finally {
            isRunning.set(false);
        }
    }

    private UUID readUuid(Object value) {
        if (value instanceof UUID id) {
            return id;
        }
        try {
            return value == null ? null : UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
