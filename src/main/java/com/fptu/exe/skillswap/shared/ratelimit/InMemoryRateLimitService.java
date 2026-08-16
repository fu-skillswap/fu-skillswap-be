package com.fptu.exe.skillswap.shared.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter fixed-window bằng Caffeine cho một application instance.
 * Tách scope cache để traffic UI không đẩy bucket auth hoặc booking ra ngoài.
 */
@Service
@Slf4j
public class InMemoryRateLimitService {

    private final Map<RateLimitScope, Cache<String, RateLimitBucket>> caches = new EnumMap<>(RateLimitScope.class);
    private final Map<RateLimitScope, Long> maximumSizes = new EnumMap<>(RateLimitScope.class);
    private final Cache<RateLimitScope, Boolean> blockLogWindows;
    private final MeterRegistry meterRegistry;

    public InMemoryRateLimitService(CacheProperties cacheProperties, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        register(RateLimitScope.SECURITY, cacheProperties.getRateLimit().getSecurity(), meterRegistry);
        register(RateLimitScope.BUSINESS, cacheProperties.getRateLimit().getBusiness(), meterRegistry);
        register(RateLimitScope.TRANSFER, cacheProperties.getRateLimit().getTransfer(), meterRegistry);
        register(RateLimitScope.BEST_EFFORT, cacheProperties.getRateLimit().getBestEffort(), meterRegistry);
        this.blockLogWindows = Caffeine.newBuilder()
                .maximumSize(RateLimitScope.values().length)
                .expireAfterWrite(Duration.ofMinutes(1))
                .build();
    }

    public void check(RateLimitScope scope, String key, int limit, Duration window, String message) {
        if (key == null || key.isBlank() || limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            return;
        }
        RateLimitScope resolvedScope = scope == null ? RateLimitScope.BUSINESS : scope;

        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        long currentWindowStart = now - (now % windowMillis);
        long expireAtEpochMilli = currentWindowStart + windowMillis;

        // Key riêng theo client key và thời điểm bắt đầu cửa sổ.
        String cacheKey = key + ":" + currentWindowStart;

        Cache<String, RateLimitBucket> cache = caches.get(resolvedScope);
        RateLimitBucket bucket = cache.getIfPresent(cacheKey);
        if (bucket == null && resolvedScope == RateLimitScope.SECURITY
                && cache.estimatedSize() >= maximumSizes.get(resolvedScope)) {
            reject(resolvedScope, limit, message, retryAfterSeconds(expireAtEpochMilli));
        }
        if (bucket == null) {
            bucket = cache.get(cacheKey, k -> new RateLimitBucket(new AtomicInteger(0), expireAtEpochMilli));
        }
        int currentCount = bucket.getCount().incrementAndGet();

        if (currentCount > limit) {
            reject(resolvedScope, limit, message, retryAfterSeconds(bucket.getExpireAtEpochMilli()));
        }
    }

    private void register(RateLimitScope scope, CacheProperties.SizedCache settings, MeterRegistry meterRegistry) {
        Cache<String, RateLimitBucket> cache = Caffeine.newBuilder()
                .maximumSize(settings.getMaximumSize())
                .recordStats()
                .expireAfter(bucketExpiry())
                .build();
        caches.put(scope, cache);
        maximumSizes.put(scope, settings.getMaximumSize());
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "rate-limit-" + scope.name().toLowerCase());
    }

    private Expiry<String, RateLimitBucket> bucketExpiry() {
        return new Expiry<>() {
            @Override
            public long expireAfterCreate(String key, RateLimitBucket bucket, long currentTime) {
                long remainingMillis = bucket.getExpireAtEpochMilli() - System.currentTimeMillis();
                return remainingMillis > 0 ? TimeUnit.MILLISECONDS.toNanos(remainingMillis) : 0;
            }

            @Override
            public long expireAfterUpdate(String key, RateLimitBucket bucket, long currentTime, long currentDuration) {
                return currentDuration;
            }

            @Override
            public long expireAfterRead(String key, RateLimitBucket bucket, long currentTime, long currentDuration) {
                return currentDuration;
            }
        };
    }

    private long retryAfterSeconds(long expireAtEpochMilli) {
        long remainingMillis = Math.max(1, expireAtEpochMilli - System.currentTimeMillis());
        return (remainingMillis + 999) / 1_000;
    }

    private void reject(RateLimitScope scope, int limit, String message, long retryAfterSeconds) {
        meterRegistry.counter("rate_limit_blocked_total", "scope", scope.name().toLowerCase()).increment();
        if (blockLogWindows.asMap().putIfAbsent(scope, Boolean.TRUE) == null) {
            log.warn("Rate limit is blocking requests in scope={}; limit={}", scope, limit);
        }
        throw new RateLimitExceededException(
                message == null || message.isBlank() ? ErrorCode.TOO_MANY_REQUESTS.getMessage() : message,
                retryAfterSeconds
        );
    }

    @Getter
    @RequiredArgsConstructor
    private static final class RateLimitBucket {
        private final AtomicInteger count;
        private final long expireAtEpochMilli;
    }
}
