package com.fptu.exe.skillswap.modules.course;

import com.fptu.exe.skillswap.modules.course.scheduler.CourseOutboxWorker;
import com.fptu.exe.skillswap.modules.course.service.CourseVaultService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseOutboxWorkerTest {

    @Test
    void claimsAndProcessesEachEventWithoutHoldingTheClaimTransaction() {
        CourseVaultService service = mock(CourseVaultService.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(service.claimCourseOutboxEvents(50)).thenReturn(List.of(first, second));

        new CourseOutboxWorker(service).processPendingOutboxEvents();

        verify(service).processCourseOutboxEvent(first);
        verify(service).processCourseOutboxEvent(second);
    }

    @Test
    void failureIsRecordedThroughTheDurableOutboxStateMachine() {
        CourseVaultService service = mock(CourseVaultService.class);
        UUID eventId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("provider unavailable");
        when(service.claimCourseOutboxEvents(50)).thenReturn(List.of(eventId));
        doThrow(failure).when(service).processCourseOutboxEvent(eventId);

        new CourseOutboxWorker(service).processPendingOutboxEvents();

        verify(service).markCourseOutboxEventFailed(eventId, failure);
    }
}
