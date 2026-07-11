package com.fptu.exe.skillswap.shared.outbox;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventOutboxTxHelper {

    private final DomainEventOutboxPollingRepository pollingRepository;
    private final DomainEventOutboxRepository domainEventOutboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<DomainEventOutbox> reserveNextBatch(int limit) {
        List<UUID> pendingIds = pollingRepository.findNextPendingBatchIds(limit);
        if (pendingIds.isEmpty()) {
            return List.of();
        }

        LocalDateTime reserveUntil = DateTimeUtil.now().plusMinutes(5);
        int reservedCount = pollingRepository.reserveBatch(pendingIds, reserveUntil);
        
        if (reservedCount == 0) {
            return List.of();
        }

        return domainEventOutboxRepository.findAllById(pendingIds);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsPublished(UUID outboxId) {
        domainEventOutboxRepository.findById(outboxId).ifPresent(outbox -> {
            outbox.setStatus(DomainEventOutboxStatus.PUBLISHED);
            outbox.setPublishedAt(DateTimeUtil.now());
            outbox.setLastError(null);
            domainEventOutboxRepository.save(outbox);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePublishFailure(UUID outboxId, int currentAttemptCount, String errorMessage) {
        domainEventOutboxRepository.findById(outboxId).ifPresent(outbox -> {
            int nextAttempt = currentAttemptCount + 1;
            outbox.setAttemptCount(nextAttempt);
            outbox.setLastError(trimError(errorMessage));
            if (nextAttempt >= 6) {
                outbox.setStatus(DomainEventOutboxStatus.FAILED);
            } else {
                outbox.setStatus(DomainEventOutboxStatus.PENDING);
                outbox.setAvailableAt(nextRetryAt(nextAttempt));
            }
            domainEventOutboxRepository.save(outbox);
        });
    }

    private LocalDateTime nextRetryAt(int attemptCount) {
        long[] backoffSeconds = {5L, 15L, 30L, 60L, 300L, 900L};
        long seconds = backoffSeconds[Math.min(Math.max(attemptCount - 1, 0), backoffSeconds.length - 1)];
        return DateTimeUtil.now().plusSeconds(seconds);
    }

    private String trimError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Rabbit publish failed";
        }
        return raw.length() <= 500 ? raw : raw.substring(0, 500);
    }
}
