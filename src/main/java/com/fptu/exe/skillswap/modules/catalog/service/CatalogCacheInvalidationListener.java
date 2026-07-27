package com.fptu.exe.skillswap.modules.catalog.service;

import com.fptu.exe.skillswap.modules.catalog.event.CatalogChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Clears catalog entries only after the source transaction commits. */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogCacheInvalidationListener {

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCatalogChanged(CatalogChangedEvent event) {
        Cache catalog = cacheManager.getCache("catalog");
        if (catalog == null) {
            return;
        }
        catalog.clear();
        log.info("Evicted catalog cache after committed catalog change from {}", event.source());
    }
}
