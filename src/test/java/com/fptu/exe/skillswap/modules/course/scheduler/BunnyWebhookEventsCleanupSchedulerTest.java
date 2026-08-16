package com.fptu.exe.skillswap.modules.course.scheduler;

import com.fptu.exe.skillswap.infrastructure.storage.StorageLifecycleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BunnyWebhookEventsCleanupSchedulerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StorageLifecycleProperties properties;

    private BunnyWebhookEventsCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new BunnyWebhookEventsCleanupScheduler(jdbcTemplate, properties);
    }

    @Test
    void executeCleanupJob_UsesReceivedAtFromWebhookSchema() {
        when(properties.getBunnyWebhookRetentionDays()).thenReturn(7);
        when(jdbcTemplate.update(anyString(), anyString(), anyInt())).thenReturn(0);

        scheduler.executeCleanupJob();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), eq("7 days"), eq(500));
        assertTrue(sqlCaptor.getValue().contains("received_at < NOW()"));
        assertFalse(sqlCaptor.getValue().contains("created_at < NOW()"));
    }
}
