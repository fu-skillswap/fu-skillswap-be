package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlogTrendingCacheTest {

    @Test
    void rankingChangesWithinDebounceWindowInvalidateOnlyOnce() {
        CacheProperties properties = new CacheProperties();
        properties.getBlog().setTrendingInvalidationDebounce(java.time.Duration.ofMinutes(1));
        BlogTrendingCache cache = new BlogTrendingCache(properties, new SimpleMeterRegistry());
        AtomicInteger loads = new AtomicInteger();

        cache.get(BlogTrendingSegment.ANONYMOUS, ignored -> {
            loads.incrementAndGet();
            return List.of(UUID.randomUUID(), UUID.randomUUID());
        });
        assertEquals(1, loads.get());

        cache.invalidateAfterRankingChange();
        for (int index = 0; index < 99; index++) {
            cache.invalidateAfterRankingChange();
        }

        cache.get(BlogTrendingSegment.ANONYMOUS, ignored -> {
            loads.incrementAndGet();
            return List.of(UUID.randomUUID());
        });
        cache.get(BlogTrendingSegment.ANONYMOUS, ignored -> {
            loads.incrementAndGet();
            return List.of(UUID.randomUUID());
        });

        assertEquals(2, loads.get(), "100 events in one window must cause only one post-invalidation reload");
    }
}
