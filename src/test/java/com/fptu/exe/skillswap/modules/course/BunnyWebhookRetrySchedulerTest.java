package com.fptu.exe.skillswap.modules.course;

import com.fptu.exe.skillswap.modules.course.scheduler.BunnyWebhookRetryScheduler;
import com.fptu.exe.skillswap.modules.course.service.CourseVaultService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BunnyWebhookRetrySchedulerTest {

    @Test
    void claimedWebhookIsProcessedByTheServiceStateMachine() {
        CourseVaultService service = mock(CourseVaultService.class);
        UUID eventId = UUID.randomUUID();
        when(service.claimWebhookEvents(50)).thenReturn(List.of(eventId));

        new BunnyWebhookRetryScheduler(service).processPendingWebhookEvents();

        verify(service).processWebhookEventIdempotent(eventId);
    }

    @Test
    void failedWebhookIsMarkedForRetryOrDeadLetterByTheService() {
        CourseVaultService service = mock(CourseVaultService.class);
        UUID eventId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("material update failed");
        when(service.claimWebhookEvents(50)).thenReturn(List.of(eventId));
        doThrow(failure).when(service).processWebhookEventIdempotent(eventId);

        new BunnyWebhookRetryScheduler(service).processPendingWebhookEvents();

        verify(service).markWebhookEventFailed(eventId, failure);
    }
}
