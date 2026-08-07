package com.fptu.exe.skillswap.modules.mail.scheduler;

import com.fptu.exe.skillswap.infrastructure.storage.StorageLifecycleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailOutboxCleanupSchedulerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StorageLifecycleProperties properties;

    private EmailOutboxCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EmailOutboxCleanupScheduler(jdbcTemplate, properties);
    }

    @Test
    void executeCleanupJob_DeletesOnlyTerminalAndOldRecords() {
        // Arrange
        when(properties.getEmailOutboxRetentionDays()).thenReturn(7);
        when(jdbcTemplate.update(anyString(), anyString(), anyInt())).thenReturn(5);

        // Act
        scheduler.executeCleanupJob();

        // Assert
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(1)).update(sqlCaptor.capture(), eq("7 days"), eq(500));

        String executedSql = sqlCaptor.getValue();
        // Check that it only deletes terminal statuses
        assertTrue(executedSql.contains("status IN ('SENT', 'FATAL_ERROR')") || 
                   executedSql.contains("status IN ('SENT','FATAL_ERROR')") || 
                   executedSql.contains("status IN ('SENT', 'FAILED')")); // Depending on exact implementation
        
        // Check that it filters by age
        assertTrue(executedSql.contains("created_at < NOW() - CAST(? AS INTERVAL)"));
        
        // Check that it limits batch size
        assertTrue(executedSql.contains("LIMIT ?") || executedSql.contains("LIMIT  ?"));
    }

    @Test
    void executeCleanupJob_Disabled_WhenRetentionIsZero() {
        // Arrange
        when(properties.getEmailOutboxRetentionDays()).thenReturn(0);

        // Act
        scheduler.executeCleanupJob();

        // Assert
        verify(jdbcTemplate, never()).update(anyString(), anyString(), anyInt());
    }
}
