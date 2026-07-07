package com.fptu.exe.skillswap.shared.ratelimit;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InMemoryRateLimitService {

    private final Map<String, FixedWindowCounter> counters = new ConcurrentHashMap<>();

    public void check(String key, int limit, Duration window, String message) {
        if (key == null || key.isBlank() || limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            return;
        }

        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        long currentWindowStart = now - (now % windowMillis);
        FixedWindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStartMillis != currentWindowStart) {
                return new FixedWindowCounter(currentWindowStart, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (counter.count.get() > limit) {
            throw new BaseException(
                    ErrorCode.TOO_MANY_REQUESTS,
                    message == null || message.isBlank() ? ErrorCode.TOO_MANY_REQUESTS.getMessage() : message
            );
        }

        cleanupExpiredEntries(now, windowMillis);
    }

    private void cleanupExpiredEntries(long now, long windowMillis) {
        if (counters.size() < 512) {
            return;
        }
        long earliestAliveWindow = now - (windowMillis * 2);
        Iterator<Map.Entry<String, FixedWindowCounter>> iterator = counters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, FixedWindowCounter> entry = iterator.next();
            if (entry.getValue().windowStartMillis < earliestAliveWindow) {
                iterator.remove();
            }
        }
    }

    private static final class FixedWindowCounter {
        private final long windowStartMillis;
        private final AtomicInteger count;

        private FixedWindowCounter(long windowStartMillis, AtomicInteger count) {
            this.windowStartMillis = windowStartMillis;
            this.count = Objects.requireNonNull(count);
        }
    }
}
