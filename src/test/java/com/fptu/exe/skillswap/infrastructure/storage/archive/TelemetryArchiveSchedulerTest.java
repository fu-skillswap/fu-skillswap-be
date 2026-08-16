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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryArchiveSchedulerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StorageArchiveService storageArchiveService;

    @Mock
    private StorageLifecycleProperties properties;

    private TelemetryArchiveScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TelemetryArchiveScheduler(
                jdbcTemplate,
                storageArchiveService,
                properties,
                new ObjectMapper()
        );
    }

    @Test
    void executeArchiveJob_ArchivesUuidTelemetryRows() {
        UUID id = UUID.randomUUID();
        when(properties.getInternalTelemetryRetentionDays()).thenReturn(14);
        when(jdbcTemplate.queryForList(anyString(), eq("14 days"), eq(1000)))
                .thenReturn(List.of(Map.of("id", id, "event_type", "MENTOR_VIEW")))
                .thenReturn(List.of());
        when(storageArchiveService.archiveJsonLines(anyString(), anyString(), anyList()))
                .thenReturn(new StorageArchiveService.ArchiveBatchResult("archive-key", "checksum", 1));
        when(jdbcTemplate.batchUpdate(anyString(), anyList())).thenReturn(new int[]{1});

        scheduler.executeArchiveJob();

        ArgumentCaptor<String> selectSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).queryForList(selectSql.capture(), eq("14 days"), eq(1000));
        assertTrue(selectSql.getValue().contains("user_id"));
        assertTrue(selectSql.getValue().contains("subject_type"));
        verify(storageArchiveService).archiveJsonLines(anyString(), eq(id + "-" + id), anyList());
    }
}
