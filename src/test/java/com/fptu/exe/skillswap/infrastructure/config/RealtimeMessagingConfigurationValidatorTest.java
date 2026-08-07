package com.fptu.exe.skillswap.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RealtimeMessagingConfigurationValidatorTest {

    @Test
    void acceptsPairedRealtimeFeatures() {
        RealtimeOutboxProperties outbox = new RealtimeOutboxProperties();
        StompRelayProperties stomp = new StompRelayProperties();
        outbox.setEnabled(true);
        stomp.setEnabled(true);

        assertDoesNotThrow(() -> new RealtimeMessagingConfigurationValidator(outbox, stomp)
                .afterSingletonsInstantiated());
    }

    @Test
    void rejectsPartiallyEnabledRealtimeTopology() {
        RealtimeOutboxProperties outbox = new RealtimeOutboxProperties();
        StompRelayProperties stomp = new StompRelayProperties();
        outbox.setEnabled(true);
        stomp.setEnabled(false);

        assertThrows(IllegalStateException.class, () -> new RealtimeMessagingConfigurationValidator(outbox, stomp)
                .afterSingletonsInstantiated());
    }
}
