package com.fptu.exe.skillswap.modules.blog.event;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Keeps public taxonomy cached while exposing only committed admin changes. */
@Component
@RequiredArgsConstructor
public class BlogTaxonomyCacheInvalidationListener {

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaxonomyChanged(BlogTaxonomyChangedEvent event) {
        Cache catalog = cacheManager.getCache("catalog");
        if (catalog == null) {
            return;
        }
        catalog.evict("blogCategories");
        catalog.evict("blogTags");
    }
}
