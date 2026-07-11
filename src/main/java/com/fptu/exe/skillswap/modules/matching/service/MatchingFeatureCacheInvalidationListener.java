package com.fptu.exe.skillswap.modules.matching.service;

import com.fptu.exe.skillswap.modules.matching.event.MatchingFeaturesInvalidationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchingFeatureCacheInvalidationListener {

    private final CachedMenteeMatchingFeatureProvider matchingFeatureProvider;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvalidation(MatchingFeaturesInvalidationEvent event) {
        if (event == null) {
            return;
        }
        if (event.invalidateAll()) {
            matchingFeatureProvider.invalidateAll();
            log.debug("Invalidated all mentee matching features cache from {}", event.source());
            return;
        }
        matchingFeatureProvider.invalidate(event.userId());
        log.debug("Invalidated mentee matching features cache for user {} from {}", event.userId(), event.source());
    }
}
