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
        configureApplicationCredentials(stomp);

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

    @Test
    void rejectsGuestCredentialsWhenRealtimeIsEnabled() {
        RealtimeOutboxProperties outbox = new RealtimeOutboxProperties();
        StompRelayProperties stomp = new StompRelayProperties();
        outbox.setEnabled(true);
        stomp.setEnabled(true);
        stomp.getRelay().setUsername("guest");
        stomp.getRelay().setPassword("guest");

        assertThrows(IllegalStateException.class, () -> new RealtimeMessagingConfigurationValidator(outbox, stomp)
                .afterSingletonsInstantiated());
    }

    @Test
    void rejectsMissingRelayCredentialsWhenRealtimeIsEnabled() {
        RealtimeOutboxProperties outbox = new RealtimeOutboxProperties();
        StompRelayProperties stomp = new StompRelayProperties();
        outbox.setEnabled(true);
        stomp.setEnabled(true);

        assertThrows(IllegalStateException.class, () -> new RealtimeMessagingConfigurationValidator(outbox, stomp)
                .afterSingletonsInstantiated());
    }

    private void configureApplicationCredentials(StompRelayProperties stomp) {
        stomp.getRelay().setUsername("skillswap");
        stomp.getRelay().setPassword("test-secret");
        stomp.setClientLogin("skillswap");
        stomp.setClientPasscode("test-secret");
        stomp.setSystemLogin("skillswap");
        stomp.setSystemPasscode("test-secret");
    }
}
