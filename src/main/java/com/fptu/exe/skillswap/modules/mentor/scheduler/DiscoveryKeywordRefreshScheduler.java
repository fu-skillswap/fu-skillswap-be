package com.fptu.exe.skillswap.modules.mentor.scheduler;

import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryKeywordSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps the optional discovery keyword cache fresh outside request handling. */
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "application.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DiscoveryKeywordRefreshScheduler {

    private final DiscoveryKeywordSupport discoveryKeywordSupport;

    @Scheduled(fixedRate = 300000)
    public void refreshKeywordsCache() {
        discoveryKeywordSupport.refreshKeywordsCache();
    }
}
