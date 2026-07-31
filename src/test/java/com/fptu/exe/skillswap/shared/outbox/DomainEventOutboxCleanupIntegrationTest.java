package com.fptu.exe.skillswap.shared.outbox;

import com.fptu.exe.skillswap.infrastructure.config.RealtimeOutboxProperties;
import com.fptu.exe.skillswap.infrastructure.realtime.DomainEventOutboxCleanupScheduler;
import com.fptu.exe.skillswap.infrastructure.testcontainer.AbstractPostgreSQLIntegrationTest;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DomainEventOutboxCleanupIntegrationTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private DomainEventOutboxRepository outboxRepository;

    @Autowired
    private DomainEventOutboxCleanupScheduler cleanupScheduler;

    @Autowired
    private RealtimeOutboxProperties properties;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
    }

    @Test
    void executeCleanupJob_shouldDeleteExpiredPublishedAndFailedEvents_andKeepUnexpired() {
        LocalDateTime now = DateTimeUtil.now();
        LocalDateTime oldTime = now.minusDays(10);
        LocalDateTime newTime = now.minusDays(1);

        // Expired PUBLISHED event
        DomainEventOutbox oldPublished = outboxRepository.save(DomainEventOutbox.builder()
                .aggregateType("BOOKING").aggregateId(UUID.randomUUID()).eventType("booking.accepted")
                .payloadJson("{}").status(DomainEventOutboxStatus.PUBLISHED)
                .availableAt(oldTime).publishedAt(oldTime).attemptCount(1).build());

        // Unexpired PUBLISHED event
        DomainEventOutbox newPublished = outboxRepository.save(DomainEventOutbox.builder()
                .aggregateType("BOOKING").aggregateId(UUID.randomUUID()).eventType("booking.accepted")
                .payloadJson("{}").status(DomainEventOutboxStatus.PUBLISHED)
                .availableAt(newTime).publishedAt(newTime).attemptCount(1).build());

        // Expired FAILED event with exhausted retries
        DomainEventOutbox oldFailedExhausted = outboxRepository.save(DomainEventOutbox.builder()
                .aggregateType("BOOKING").aggregateId(UUID.randomUUID()).eventType("booking.failed")
                .payloadJson("{}").status(DomainEventOutboxStatus.FAILED)
                .availableAt(oldTime).publishedAt(null).attemptCount(properties.getMaxRetryAttempts()).build());

        // Expired FAILED event but still has retries remaining
        DomainEventOutbox oldFailedRetryable = outboxRepository.save(DomainEventOutbox.builder()
                .aggregateType("BOOKING").aggregateId(UUID.randomUUID()).eventType("booking.failed")
                .payloadJson("{}").status(DomainEventOutboxStatus.FAILED)
                .availableAt(oldTime).publishedAt(null).attemptCount(1).build());

        int deletedCount = cleanupScheduler.executeCleanupJob();

        assertEquals(2, deletedCount);
        assertFalse(outboxRepository.existsById(oldPublished.getId()));
        assertFalse(outboxRepository.existsById(oldFailedExhausted.getId()));

        assertTrue(outboxRepository.existsById(newPublished.getId()));
        assertTrue(outboxRepository.existsById(oldFailedRetryable.getId()));
    }

    @Test
    void executeCleanupJob_concurrentExecution_shouldDeleteAllExpiredRowsWithoutConflict() throws Exception {
        LocalDateTime oldTime = DateTimeUtil.now().minusDays(10);

        for (int i = 0; i < 20; i++) {
            outboxRepository.save(DomainEventOutbox.builder()
                    .aggregateType("TEST").aggregateId(UUID.randomUUID()).eventType("test.event")
                    .payloadJson("{}").status(DomainEventOutboxStatus.PUBLISHED)
                    .availableAt(oldTime).publishedAt(oldTime).attemptCount(1).build());
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Integer> task = () -> cleanupScheduler.executeCleanupJob();

        List<Future<Integer>> futures = executor.invokeAll(List.of(task, task));
        executor.shutdown();

        int totalDeleted = 0;
        for (Future<Integer> f : futures) {
            totalDeleted += f.get();
        }

        assertEquals(20, totalDeleted);
        assertEquals(0, outboxRepository.count());
    }
}
