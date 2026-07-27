package com.fptu.exe.skillswap.modules.matching.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import com.fptu.exe.skillswap.modules.matching.service.MenteeMatchingFeatures;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class MatchingFeatureCacheConfig {

    private final CacheProperties cacheProperties;
    private final MeterRegistry meterRegistry;

    public MatchingFeatureCacheConfig(CacheProperties cacheProperties, MeterRegistry meterRegistry) {
        this.cacheProperties = cacheProperties;
        this.meterRegistry = meterRegistry;
    }

    @Bean
    public Cache<UUID, MenteeMatchingFeatures> menteeMatchingFeaturesCache() {
        CacheProperties.TimedCache settings = cacheProperties.getMatchingFeatures();
        Cache<UUID, MenteeMatchingFeatures> cache = Caffeine.newBuilder()
                .maximumSize(settings.getMaximumSize())
                .expireAfterWrite(settings.getTtl())
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "matching-features");
        return cache;
    }
}
