package com.fptu.exe.skillswap.modules.forum.scheduler;

import com.fptu.exe.skillswap.infrastructure.storage.StorageLifecycleProperties;
import com.fptu.exe.skillswap.modules.forum.service.ForumRateLimitBucketCleanupService;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumRateLimitBucketCleanupSchedulerTest {

    @Mock
    private ForumRateLimitBucketCleanupService cleanupService;

    @Mock
    private StorageLifecycleProperties properties;

    private ForumRateLimitBucketCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ForumRateLimitBucketCleanupScheduler(
                cleanupService,
                properties,
                TimeProvider.fixedUtc(Instant.parse("2026-09-05T12:00:00Z")));
    }

    @Test
    void cleanup_deletesExpiredRowsInBatchesUsingRetentionCutoff() {
        when(properties.getForumRateLimitRetentionDays()).thenReturn(2);
        when(properties.getCleanupBatchSize()).thenReturn(500);
        when(cleanupService.deleteSingleExpiredBatch(any(), eq(500))).thenReturn(500, 7);

        assertEquals(507, scheduler.executeCleanupJob());

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(cleanupService, times(2)).deleteSingleExpiredBatch(cutoffCaptor.capture(), eq(500));
        assertEquals(LocalDateTime.of(2026, 9, 3, 12, 0), cutoffCaptor.getAllValues().get(0));
        assertEquals(cutoffCaptor.getAllValues().get(0), cutoffCaptor.getAllValues().get(1));
    }

    @Test
    void cleanup_disabledWhenRetentionIsNotPositive() {
        when(properties.getForumRateLimitRetentionDays()).thenReturn(0);

        assertEquals(0, scheduler.executeCleanupJob());

        verify(cleanupService, times(0)).deleteSingleExpiredBatch(any(), any(Integer.class));
    }
}
