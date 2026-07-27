package com.fptu.exe.skillswap.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class MasterDataCacheConfig {

    private final CacheProperties cacheProperties;
    private final MeterRegistry meterRegistry;

    @Bean
    @Primary
    public CacheManager cacheManager() {
        CacheProperties.TimedCache settings = cacheProperties.getCatalog();
        Cache<Object, Object> catalogCache = Caffeine.newBuilder()
                .maximumSize(settings.getMaximumSize())
                .expireAfterWrite(settings.getTtl())
                .recordStats()
                .build();
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.registerCustomCache("catalog", catalogCache);
        cacheManager.setAllowNullValues(false);
        CaffeineCacheMetrics.monitor(meterRegistry, catalogCache, "catalog");
        return cacheManager;
    }
}
