package com.fptu.exe.skillswap.modules.booking.scheduler;

import com.fptu.exe.skillswap.modules.booking.service.GroupSessionSeatReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.group-sessions", name = "enabled", havingValue = "true")
public class GroupSessionSeatReconciliationScheduler {
    private final GroupSessionSeatReconciliationService reconciliationService;

    @Scheduled(fixedDelayString = "${application.group-sessions.seat-reconciliation-delay-ms:3600000}")
    public void reconcileUpcomingSeats() {
        reconciliationService.reconcileUpcomingSeats();
    }

    @Scheduled(cron = "${application.group-sessions.seat-terminal-audit-cron:0 20 3 * * *}")
    public void auditTerminalSeatCounters() {
        reconciliationService.auditTerminalSeatCounters();
    }
}
