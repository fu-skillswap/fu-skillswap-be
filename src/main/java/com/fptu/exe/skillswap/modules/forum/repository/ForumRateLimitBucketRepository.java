package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumRateLimitBucket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForumRateLimitBucketRepository extends JpaRepository<ForumRateLimitBucket, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ForumRateLimitBucket> findByBucketKeyAndWindowStartedAt(String bucketKey, LocalDateTime windowStartedAt);

    @Modifying
    @Query("delete from ForumRateLimitBucket b where b.bucketKey = :bucketKey and b.windowExpiresAt <= :now")
    int deleteExpiredForKey(@Param("bucketKey") String bucketKey, @Param("now") LocalDateTime now);

    @Query(value = """
            SELECT id
            FROM forum_rate_limit_buckets
            WHERE window_expires_at <= :cutoff
            ORDER BY window_expires_at ASC, id ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> findExpiredIdsForUpdateSkipLocked(@Param("cutoff") LocalDateTime cutoff,
                                                  @Param("batchSize") int batchSize);

    @Modifying
    @Query(value = """
            DELETE FROM forum_rate_limit_buckets
            WHERE id IN (:ids)
              AND window_expires_at <= :cutoff
            """, nativeQuery = true)
    int deleteExpiredByIdsAndCutoff(@Param("ids") List<UUID> ids,
                                    @Param("cutoff") LocalDateTime cutoff);
}
