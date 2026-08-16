package com.fptu.exe.skillswap.shared.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Spring bean riêng xử lý xóa batch theo transaction tách biệt. */
@Service
@RequiredArgsConstructor
public class DomainEventOutboxBatchCleanupService {

    private final DomainEventOutboxRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeoutString = "${application.realtime.outbox.batch-transaction-timeout-seconds:10}")
    public int deleteSinglePublishedBatch(LocalDateTime publishedBefore, int batchSize) {
        List<UUID> ids = outboxRepository.findExpiredPublishedIdsForUpdateSkipLocked(publishedBefore, batchSize);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return outboxRepository.deletePublishedByIdsAndCutoff(ids, publishedBefore);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeoutString = "${application.realtime.outbox.batch-transaction-timeout-seconds:10}")
    public int deleteSingleFailedBatch(LocalDateTime failedBefore, int maxAttempts, int batchSize) {
        List<UUID> ids = outboxRepository.findExpiredFailedIdsForUpdateSkipLocked(failedBefore, maxAttempts, batchSize);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return outboxRepository.deleteFailedByIdsAndCutoff(ids, failedBefore, maxAttempts);
    }
}
