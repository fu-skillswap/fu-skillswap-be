package com.fptu.exe.skillswap.modules.blog.service;

import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Local blog caches. Values are neutral identifiers or best-effort dedupe keys. */
@Component
public class BlogTrendingCache {

    private final Cache<BlogTrendingSegment, List<UUID>> cache;
    private final Cache<String, Boolean> invalidationDebounce;
    private final Cache<String, Boolean> viewDedupeCache;

    public BlogTrendingCache(CacheProperties cacheProperties, MeterRegistry meterRegistry) {
        CacheProperties.BlogCache settings = cacheProperties.getBlog();
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(settings.getTrending().getTtl())
                .maximumSize(settings.getTrending().getMaximumSize())
                .recordStats()
                .build();
        this.invalidationDebounce = Caffeine.newBuilder()
                .maximumSize(settings.getTrendingInvalidationDebounceMaximumSize())
                .expireAfterWrite(settings.getTrendingInvalidationDebounce())
                .build();
        this.viewDedupeCache = Caffeine.newBuilder()
                .expireAfterWrite(settings.getViewDedupe().getTtl())
                .maximumSize(settings.getViewDedupe().getMaximumSize())
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "blog-trending");
        CaffeineCacheMetrics.monitor(meterRegistry, viewDedupeCache, "blog-view-dedupe");
    }

    public List<UUID> get(BlogTrendingSegment segment, Function<BlogTrendingSegment, List<UUID>> loader) {
        return cache.get(segment, key -> List.copyOf(loader.apply(key)));
    }

    /** The first ranking change in a short interval invalidates; later ones reuse the same snapshot. */
    public void invalidateAfterRankingChange() {
        if (invalidationDebounce.asMap().putIfAbsent("blog-trending", Boolean.TRUE) == null) {
            cache.invalidateAll();
        }
    }

    public boolean registerUniqueView(String dedupeKey) {
        return viewDedupeCache.asMap().putIfAbsent(dedupeKey, Boolean.TRUE) == null;
    }
}
