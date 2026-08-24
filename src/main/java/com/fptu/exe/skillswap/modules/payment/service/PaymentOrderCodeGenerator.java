package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PaymentOrderCodeGenerator {

    public static final long PAYOS_MAX_SAFE_ORDER_CODE = 9_007_199_254_740_991L;
    private static final long PROVIDER_ORDER_CODE_EPOCH_MILLIS = LocalDateTime.of(2025, 1, 1, 0, 0)
            .atZone(TimeProvider.BUSINESS_ZONE)
            .toInstant()
            .toEpochMilli();
    private static final int PROVIDER_ORDER_CODE_SEQUENCE_MOD = 10_000;
    private static final AtomicLong PROVIDER_ORDER_CODE_LAST_BUCKET = new AtomicLong(-1L);
    private static final AtomicInteger PROVIDER_ORDER_CODE_SEQUENCE = new AtomicInteger(0);
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    public String generateOrderCode(UUID id) {
        if (id == null) {
            return "PAY-UNKNOWN";
        }
        return "PAY-" + id.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    public long generateProviderOrderCode(UUID id, int attemptNo) {
        while (true) {
            long bucket = Math.max(0L, timeProvider.instant().toEpochMilli() - PROVIDER_ORDER_CODE_EPOCH_MILLIS);
            int sequence = nextProviderOrderCodeSequence(bucket);
            if (sequence < 0) {
                Thread.onSpinWait();
                continue;
            }
            long candidate = bucket * PROVIDER_ORDER_CODE_SEQUENCE_MOD + sequence;
            if (candidate > 0 && candidate <= PAYOS_MAX_SAFE_ORDER_CODE) {
                return candidate;
            }
        }
    }

    private int nextProviderOrderCodeSequence(long bucket) {
        while (true) {
            long lastBucket = PROVIDER_ORDER_CODE_LAST_BUCKET.get();
            if (lastBucket != bucket) {
                if (PROVIDER_ORDER_CODE_LAST_BUCKET.compareAndSet(lastBucket, bucket)) {
                    PROVIDER_ORDER_CODE_SEQUENCE.set(Math.floorMod(attemptNoSeed(bucket), PROVIDER_ORDER_CODE_SEQUENCE_MOD));
                }
                continue;
            }

            int current = PROVIDER_ORDER_CODE_SEQUENCE.getAndIncrement();
            if (current < PROVIDER_ORDER_CODE_SEQUENCE_MOD) {
                return current;
            }

            return -1;
        }
    }

    private int attemptNoSeed(long bucket) {
        long mixed = bucket ^ (bucket >>> 17) ^ (bucket >>> 31);
        return (int) Math.floorMod(mixed, PROVIDER_ORDER_CODE_SEQUENCE_MOD);
    }
}
