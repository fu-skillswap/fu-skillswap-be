package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumRateLimitBucket;
import com.fptu.exe.skillswap.modules.forum.repository.ForumRateLimitBucketRepository;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.ratelimit.RateLimitExceededException;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Clock;

/** Database-backed fixed-window limiter for forum abuse controls across instances. */
@Service
@RequiredArgsConstructor
public class ForumDatabaseRateLimitService {

    private final ForumRateLimitBucketRepository bucketRepository;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    /**
     * Checks and updates the rate-limit bucket in an independent transaction.
     * <p>
     * NOTE: Uses Propagation.REQUIRES_NEW so that:
     * 1) Rate limit increments commit independently of caller transaction success or rollback.
     * 2) {@link Retryable} on {@link DataIntegrityViolationException} can start a fresh transaction.
     * <p>
     * IMPORTANT: Callers MUST invoke this method BEFORE opening any business write transactions
     * (e.g. as a preflight check outside TransactionTemplate) to avoid HikariCP connection pool starvation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Retryable(
            retryFor = DataIntegrityViolationException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 20, maxDelay = 200, multiplier = 2)
    )
    public void check(String key, int limit, Duration window, String message) {
        if (key == null || key.isBlank() || limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            return;
        }
        if (window.getSeconds() <= 0) {
            return;
        }

        Instant nowInstant = timeProvider.instant();
        long nowEpochSecond = nowInstant.getEpochSecond();
        long windowSeconds = window.getSeconds();
        long windowStartEpochSecond = nowEpochSecond - Math.floorMod(nowEpochSecond, windowSeconds);
        LocalDateTime windowStartedAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(windowStartEpochSecond), TimeProvider.UTC_ZONE);
        LocalDateTime windowExpiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(windowStartEpochSecond + windowSeconds), TimeProvider.UTC_ZONE);
        LocalDateTime now = LocalDateTime.ofInstant(nowInstant, TimeProvider.UTC_ZONE);

        bucketRepository.deleteExpiredForKey(key, now);
        ForumRateLimitBucket bucket = bucketRepository
                .findByBucketKeyAndWindowStartedAt(key, windowStartedAt)
                .orElseGet(() -> bucketRepository.saveAndFlush(ForumRateLimitBucket.builder()
                        .bucketKey(key)
                        .windowStartedAt(windowStartedAt)
                        .windowExpiresAt(windowExpiresAt)
                        .requestCount(0)
                        .build()));

        bucket.setRequestCount(bucket.getRequestCount() + 1);
        if (bucket.getRequestCount() > limit) {
            throw new RateLimitExceededException(
                    message == null || message.isBlank() ? ErrorCode.TOO_MANY_REQUESTS.getMessage() : message,
                    retryAfterSeconds(windowExpiresAt)
            );
        }
        bucketRepository.save(bucket);
    }

    private long retryAfterSeconds(LocalDateTime expiresAt) {
        long remainingMillis = Duration.between(
                LocalDateTime.ofInstant(timeProvider.instant(), TimeProvider.UTC_ZONE), expiresAt).toMillis();
        return Math.max(1, (remainingMillis + 999) / 1_000);
    }
}
