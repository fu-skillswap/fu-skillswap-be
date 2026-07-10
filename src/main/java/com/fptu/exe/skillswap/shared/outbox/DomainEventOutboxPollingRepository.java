package com.fptu.exe.skillswap.shared.outbox;

import java.util.List;

public interface DomainEventOutboxPollingRepository {

    java.util.List<java.util.UUID> findNextPendingBatchIds(int limit);
    int reserveBatch(java.util.List<java.util.UUID> ids, java.time.LocalDateTime nextAvailableAt);
}
