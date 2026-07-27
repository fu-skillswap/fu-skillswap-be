package com.fptu.exe.skillswap.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class UserBanStatusCacheConfig {

    private final CacheProperties cacheProperties;
    private final MeterRegistry meterRegistry;

    public UserBanStatusCacheConfig(CacheProperties cacheProperties, MeterRegistry meterRegistry) {
        this.cacheProperties = cacheProperties;
        this.meterRegistry = meterRegistry;
    }

    @Bean
    public Cache<UUID, Boolean> userBanStatusCache() {
        CacheProperties.TimedCache settings = cacheProperties.getUserBanStatus();
        Cache<UUID, Boolean> cache = Caffeine.newBuilder()
                .maximumSize(settings.getMaximumSize())
                .expireAfterWrite(settings.getTtl())
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "user-ban-status");
        return cache;
    }
}
