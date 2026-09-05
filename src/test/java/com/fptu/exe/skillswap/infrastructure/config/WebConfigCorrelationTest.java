package com.fptu.exe.skillswap.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebConfigCorrelationTest {

    @Test
    void correlationHeaderCanBeSentAndReadByBrowserClients() {
        WebConfig webConfig = new WebConfig(new MockEnvironment()
                .withProperty("application.cors.allowed-origin-patterns", "http://localhost:3000"));

        var cors = webConfig.corsConfigurationSource()
                .getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/test"));

        assertTrue(cors.getAllowedHeaders().contains("X-Correlation-ID"));
        assertTrue(cors.getExposedHeaders().contains("X-Correlation-ID"));
    }
}
