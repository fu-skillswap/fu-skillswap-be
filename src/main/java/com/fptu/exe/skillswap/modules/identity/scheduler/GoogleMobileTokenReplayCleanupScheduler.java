package com.fptu.exe.skillswap.modules.identity.scheduler;

import com.fptu.exe.skillswap.modules.identity.repository.GoogleMobileTokenReplayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "application.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GoogleMobileTokenReplayCleanupScheduler {

    private final GoogleMobileTokenReplayRepository replayRepository;

    @Scheduled(cron = "${application.google.mobile-replay-cleanup-cron:0 */10 * * * *}", zone = "Asia/Ho_Chi_Minh")
    public void deleteExpired() {
        int deleted = replayRepository.deleteExpired();
        if (deleted > 0) {
            log.info("Google Mobile token replay cleanup completed. deleted={}", deleted);
        }
    }
}
