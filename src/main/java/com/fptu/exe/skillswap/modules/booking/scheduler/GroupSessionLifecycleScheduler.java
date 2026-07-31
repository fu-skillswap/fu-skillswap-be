package com.fptu.exe.skillswap.modules.booking.scheduler;

import com.fptu.exe.skillswap.modules.booking.service.GroupSessionLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.group-sessions", name = "enabled", havingValue = "true")
public class GroupSessionLifecycleScheduler {

    private final GroupSessionLifecycleService lifecycleService;

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void processLifecycle() {
        try {
            int changed = lifecycleService.processDueSessions();
            if (changed > 0) {
                log.info("Processed {} group-session lifecycle transitions", changed);
            }
        } catch (Exception exception) {
            log.error("Group-session lifecycle processing failed", exception);
        }
    }
}
