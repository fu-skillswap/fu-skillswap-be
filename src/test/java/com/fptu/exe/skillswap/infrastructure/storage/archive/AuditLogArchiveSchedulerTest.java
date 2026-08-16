package com.fptu.exe.skillswap.infrastructure.storage.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.storage.StorageLifecycleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

@ExtendWith(MockitoExtension.class)
class AuditLogArchiveSchedulerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StorageArchiveService storageArchiveService;

    @Mock
    private StorageLifecycleProperties properties;

    private AuditLogArchiveScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AuditLogArchiveScheduler(
                jdbcTemplate,
                storageArchiveService,
                properties,
                new ObjectMapper()
        );
    }

    @Test
    void executeArchiveJob_UsesAuditLogSchemaAndUuidIds() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(properties.getAuditLogRetentionDays()).thenReturn(90);
        when(jdbcTemplate.queryForList(anyString(), eq("90 days"), eq(1000)))
                .thenReturn(List.of(
                        Map.of("id", firstId, "action", "CREATE", "entity_type", "BOOKING"),
                        Map.of("id", secondId, "action", "UPDATE", "entity_type", "BOOKING")
                ))
                .thenReturn(List.of());
        when(storageArchiveService.archiveJsonLines(anyString(), anyString(), anyList()))
                .thenReturn(new StorageArchiveService.ArchiveBatchResult("archive-key", "checksum", 2));
        when(jdbcTemplate.batchUpdate(anyString(), anyList())).thenReturn(new int[]{1, 1});

        scheduler.executeArchiveJob();

        ArgumentCaptor<String> selectSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).queryForList(selectSql.capture(), eq("90 days"), eq(1000));
        assertTrue(selectSql.getValue().contains("actor_user_id"));
        assertTrue(selectSql.getValue().contains("entity_type"));
        assertFalse(selectSql.getValue().contains("created_by"));
        assertFalse(selectSql.getValue().contains("entity_name"));

        verify(storageArchiveService).archiveJsonLines(
                anyString(),
                eq(firstId + "-" + secondId),
                anyList()
        );
    }
}
