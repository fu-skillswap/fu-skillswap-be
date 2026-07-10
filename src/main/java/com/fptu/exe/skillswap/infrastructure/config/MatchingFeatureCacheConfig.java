package com.fptu.exe.skillswap.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fptu.exe.skillswap.modules.matching.service.MenteeMatchingFeatures;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.UUID;

@Configuration
public class MatchingFeatureCacheConfig {

    @Bean
    public Cache<UUID, MenteeMatchingFeatures> menteeMatchingFeaturesCache() {
        return Caffeine.newBuilder()
                .maximumSize(20_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();
    }
}
