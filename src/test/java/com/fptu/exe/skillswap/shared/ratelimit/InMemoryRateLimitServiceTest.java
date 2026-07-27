package com.fptu.exe.skillswap.shared.ratelimit;

import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryRateLimitServiceTest {

    @Test
    void securityCapacityDoesNotEvictOrResetBusinessBuckets() {
        CacheProperties properties = new CacheProperties();
        properties.getRateLimit().getSecurity().setMaximumSize(1);
        properties.getRateLimit().getBusiness().setMaximumSize(1);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        InMemoryRateLimitService service = new InMemoryRateLimitService(properties, registry);

        service.check(RateLimitScope.SECURITY, "auth:first", 10, Duration.ofMinutes(1), "blocked");

        BaseException rejection = assertThrows(BaseException.class,
                () -> service.check(RateLimitScope.SECURITY, "auth:second", 10, Duration.ofMinutes(1), "blocked"));
        assertEquals("TOO_MANY_REQUESTS", rejection.getErrorCode().name());

        assertDoesNotThrow(() -> service.check(
                RateLimitScope.BUSINESS, "booking:first", 10, Duration.ofMinutes(1), "blocked"));
        assertEquals(1.0, registry.get("rate_limit_blocked_total")
                .tag("scope", "security")
                .counter()
                .count());
    }
}
