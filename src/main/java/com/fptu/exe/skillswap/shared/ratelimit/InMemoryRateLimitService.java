package com.fptu.exe.skillswap.shared.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Caffeine-backed fixed-window rate limiter.
 * Replaces the previous manual ConcurrentHashMap implementation to prevent memory leaks,
 * automatically evicting expired window buckets and capping maximum size.
 */
@Service
@Slf4j
public class InMemoryRateLimitService {

    // Cap the cache to 10,000 active rate-limit windows to prevent memory exhaustion (DoS protection)
    private final Cache<String, RateLimitBucket> cache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfter(new Expiry<String, RateLimitBucket>() {
                @Override
                public long expireAfterCreate(String key, RateLimitBucket bucket, long currentTime) {
                    long remainingMillis = bucket.getExpireAtEpochMilli() - System.currentTimeMillis();
                    return remainingMillis > 0 ? TimeUnit.MILLISECONDS.toNanos(remainingMillis) : 0;
                }

                @Override
                public long expireAfterUpdate(String key, RateLimitBucket bucket, long currentTime, long currentDuration) {
                    return currentDuration; // Keep original duration
                }

                @Override
                public long expireAfterRead(String key, RateLimitBucket bucket, long currentTime, long currentDuration) {
                    return currentDuration; // Keep original duration
                }
            })
            .build();

    public void check(String key, int limit, Duration window, String message) {
        if (key == null || key.isBlank() || limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            return;
        }

        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        long currentWindowStart = now - (now % windowMillis);
        long expireAtEpochMilli = currentWindowStart + windowMillis;

        // Unique key for the key + window window start time
        String cacheKey = key + ":" + currentWindowStart;

        RateLimitBucket bucket = cache.get(cacheKey, k -> new RateLimitBucket(new AtomicInteger(0), expireAtEpochMilli));
        int currentCount = bucket.getCount().incrementAndGet();

        if (currentCount > limit) {
            log.warn("Rate limit exceeded for key={}. limit={}, count={}", key, limit, currentCount);
            throw new BaseException(
                    ErrorCode.TOO_MANY_REQUESTS,
                    message == null || message.isBlank() ? ErrorCode.TOO_MANY_REQUESTS.getMessage() : message
            );
        }
    }

    @Getter
    @RequiredArgsConstructor
    private static final class RateLimitBucket {
        private final AtomicInteger count;
        private final long expireAtEpochMilli;
    }
}
