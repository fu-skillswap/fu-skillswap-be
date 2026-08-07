package com.fptu.exe.skillswap.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryPropertiesTest {

    @Test
    void shouldNormalizeInvalidValuesToSafeDefaults() {
        DiscoveryProperties properties = new DiscoveryProperties(0, " ");

        assertEquals(100, properties.recallWindowSize());
        assertEquals("structured-v1", properties.recommendationAlgorithmVersion());
    }

    @Test
    void shouldTrimAndKeepConfiguredValues() {
        DiscoveryProperties properties = new DiscoveryProperties(250, " custom-v2 ");

        assertEquals(250, properties.recallWindowSize());
        assertEquals("custom-v2", properties.recommendationAlgorithmVersion());
    }
}
