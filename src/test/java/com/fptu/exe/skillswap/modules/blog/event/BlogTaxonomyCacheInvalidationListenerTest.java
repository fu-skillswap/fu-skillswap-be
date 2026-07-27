package com.fptu.exe.skillswap.modules.blog.event;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlogTaxonomyCacheInvalidationListenerTest {

    @Test
    void committedTaxonomyChangeEvictsOnlyPublicTaxonomyKeys() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache catalog = mock(Cache.class);
        when(cacheManager.getCache("catalog")).thenReturn(catalog);
        BlogTaxonomyCacheInvalidationListener listener = new BlogTaxonomyCacheInvalidationListener(cacheManager);

        listener.onTaxonomyChanged(new BlogTaxonomyChangedEvent());

        verify(catalog).evict("blogCategories");
        verify(catalog).evict("blogTags");
    }
}
