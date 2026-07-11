package com.fptu.exe.skillswap.infrastructure.websocket;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StompSessionRegistryTest {

    @Test
    void removeShouldBeIdempotent() {
        StompSessionRegistry registry = new StompSessionRegistry();
        UUID userId = UUID.randomUUID();

        registry.register("session-1", userId);
        assertEquals(userId, registry.remove("session-1"));
        assertNull(registry.remove("session-1"));
    }
}
