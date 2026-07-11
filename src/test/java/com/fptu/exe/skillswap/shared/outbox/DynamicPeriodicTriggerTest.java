package com.fptu.exe.skillswap.shared.outbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicPeriodicTriggerTest {

    @Test
    void shouldBackoffAndResetDelay() {
        DynamicPeriodicTrigger trigger = new DynamicPeriodicTrigger(100L, 2000L);

        assertEquals(100L, trigger.currentDelayMs());

        trigger.recordPollResult(false);
        assertEquals(500L, trigger.currentDelayMs());

        trigger.recordPollResult(false);
        assertEquals(1000L, trigger.currentDelayMs());

        trigger.recordPollResult(true);
        assertEquals(100L, trigger.currentDelayMs());
    }
}
