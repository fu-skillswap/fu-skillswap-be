package com.fptu.exe.skillswap.modules.booking.scheduler;

import com.fptu.exe.skillswap.infrastructure.config.AvailabilityTemplateProperties;
import com.fptu.exe.skillswap.modules.booking.service.AvailabilityTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AvailabilityTemplateScheduler {
    private final AvailabilityTemplateService templateService;
    private final AvailabilityTemplateProperties properties;

    @Scheduled(cron = "0 */15 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void reconcileDueTemplates() {
        if (!properties.templatesEnabled()) return;
        int processed = 0;
        while (processed < properties.schedulerMaxTemplatesPerRun()) {
            var claims = templateService.claimDueTemplates();
            if (claims.isEmpty()) return;
            for (String claim : claims) {
                if (processed++ >= properties.schedulerMaxTemplatesPerRun()) break;
                templateService.reconcileClaim(claim);
            }
        }
        log.warn("Availability template reconciliation reached configured run cap of {}", properties.schedulerMaxTemplatesPerRun());
    }
}
