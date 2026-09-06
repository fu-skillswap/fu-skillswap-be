package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.repository.ForumRateLimitBucketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Deletes expired forum rate-limit buckets in short, independently committed batches. */
@Service
@RequiredArgsConstructor
public class ForumRateLimitBucketCleanupService {

    private final ForumRateLimitBucketRepository bucketRepository;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            timeoutString = "${application.storage.lifecycle.forum-rate-limit-cleanup-transaction-timeout-seconds:10}"
    )
    public int deleteSingleExpiredBatch(LocalDateTime cutoff, int batchSize) {
        List<UUID> ids = bucketRepository.findExpiredIdsForUpdateSkipLocked(cutoff, batchSize);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return bucketRepository.deleteExpiredByIdsAndCutoff(ids, cutoff);
    }
}
