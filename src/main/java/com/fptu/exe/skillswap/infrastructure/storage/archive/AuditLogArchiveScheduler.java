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

    @Scheduled(cron = "${application.storage.lifecycle.audit-log-archive-cron:0 0 4 * * *}")
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
        int maxBatches = 50; // max 50k records per run
        int totalArchived = 0;

        // PG JSONB column cast to text implicitly in JDBC map? It usually comes as PGobject.
        // We will just fetch everything and let Jackson serialize it.
        String selectSql = "SELECT id, created_at, created_by, entity_name, entity_id, action, old_value, new_value " +
                "FROM audit_logs " +
                "WHERE created_at < NOW() - CAST(? AS INTERVAL) " +
                "ORDER BY id ASC LIMIT ?";

        String deleteSql = "DELETE FROM audit_logs WHERE id = ?";

        for (int i = 0; i < maxBatches; i++) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql, retentionDays + " days", batchSize);
            if (rows.isEmpty()) {
                break;
            }

            List<String> jsonLines = new ArrayList<>();
            List<Long> idsToDelete = new ArrayList<>();
            long firstId = -1;
            long lastId = -1;

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Map<String, Object> row = rows.get(rowIndex);
                long id = ((Number) row.get("id")).longValue();
                
                if (rowIndex == 0) firstId = id;
                if (rowIndex == rows.size() - 1) lastId = id;

                try {
                    jsonLines.add(objectMapper.writeValueAsString(row));
                    idsToDelete.add(id);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize audit log row id={}", id, e);
                }
            }

            if (!jsonLines.isEmpty()) {
                LocalDateTime now = LocalDateTime.now();
                String prefix = String.format("archives/audit_logs/%04d/%02d/%02d", 
                        now.getYear(), now.getMonthValue(), now.getDayOfMonth());
                
                try {
                    StorageArchiveService.ArchiveBatchResult result = storageArchiveService.archiveJsonLines(prefix, firstId, lastId, jsonLines);
                    if (result != null) {
                        List<Object[]> batchArgs = idsToDelete.stream()
                                .map(id -> new Object[]{id})
                                .collect(Collectors.toList());
                        int[] updateCounts = jdbcTemplate.batchUpdate(deleteSql, batchArgs);
                        int deletedCount = 0;
                        for (int count : updateCounts) {
                            deletedCount += (count > 0) ? count : (count == java.sql.Statement.SUCCESS_NO_INFO ? 1 : 0);
                        }
                        
                        if (deletedCount != idsToDelete.size()) {
                            log.error("Integrity error: Deleted {} rows but archived {}. Halting batch to prevent data inconsistency.", deletedCount, idsToDelete.size());
                            break;
                        }
                        totalArchived += deletedCount;
                    } else {
                        log.warn("Archiving returned null result, skipping deletion for this batch.");
                        break;
                    }
                } catch (Exception ex) {
                    log.error("Archive batch failed (firstId={}, lastId={}). Aborting batch delete to prevent data loss.", firstId, lastId, ex);
                    break; // Stop further processing on failure to ensure safety
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Finished AuditLog archive job. Archived {} records in {} ms", totalArchived, duration);
        } finally {
            isRunning.set(false);
        }
    }
}
