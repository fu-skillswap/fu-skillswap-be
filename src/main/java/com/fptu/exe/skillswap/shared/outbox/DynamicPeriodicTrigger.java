package com.fptu.exe.skillswap.shared.outbox;

import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class DynamicPeriodicTrigger implements Trigger {

    private static final long[] BACKOFF_MULTIPLIERS = {1L, 5L, 10L, 20L};

    private final long baseDelayMs;
    private final long maxDelayMs;
    private final AtomicInteger backoffStep = new AtomicInteger(0);

    public DynamicPeriodicTrigger(long baseDelayMs, long maxDelayMs) {
        this.baseDelayMs = Math.max(100L, baseDelayMs);
        this.maxDelayMs = Math.max(this.baseDelayMs, maxDelayMs);
    }

    public void recordPollResult(boolean hadWork) {
        if (hadWork) {
            backoffStep.set(0);
            return;
        }
        backoffStep.updateAndGet(step -> Math.min(step + 1, BACKOFF_MULTIPLIERS.length - 1));
    }

    public long currentDelayMs() {
        return Math.min(maxDelayMs, baseDelayMs * BACKOFF_MULTIPLIERS[backoffStep.get()]);
    }

    @Override
    public Instant nextExecution(TriggerContext triggerContext) {
        Instant lastCompletion = triggerContext.lastCompletion();
        Instant reference = lastCompletion != null ? lastCompletion : Instant.now();
        return reference.plusMillis(currentDelayMs());
    }
}
