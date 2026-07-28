package com.fptu.exe.skillswap.modules.identity.scheduler;

import com.fptu.exe.skillswap.modules.identity.service.GoogleCalendarSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "application.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarSyncScheduler {

    private final GoogleCalendarSyncService googleCalendarSyncService;

    @Scheduled(fixedDelayString = "${application.google.calendar-sync-poll-ms:60000}")
    public void processDueJobs() {
        try {
            googleCalendarSyncService.processDueJobs();
        } catch (Exception ex) {
            log.error("Google calendar sync scheduler failed", ex);
        }
    }
}
