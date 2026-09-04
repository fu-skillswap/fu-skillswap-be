package com.fptu.exe.skillswap.modules.course.scheduler;

import com.fptu.exe.skillswap.modules.course.service.CourseSettlementService;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Bounded recovery loop for the local escrow lifecycle; it never calls external providers. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "application.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CourseSettlementLifecycleScheduler {

    private final CourseSettlementService courseSettlementService;
    private final TimeProvider timeProvider;

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void progressEscrowLifecycle() {
        try {
            int eligible = courseSettlementService.markEligibleSettlements();
            Instant now = timeProvider.instant();
            int released = 0;
            for (UUID allocationId : courseSettlementService.findEligibleAllocationIdsBefore(now)) {
                if (courseSettlementService.releaseEligibleAllocation(allocationId, now)) {
                    released++;
                }
            }
            if (eligible > 0 || released > 0) {
                log.info("Course escrow lifecycle progressed: eligible={}, released={}", eligible, released);
            }
        } catch (RuntimeException ex) {
            log.error("Course escrow lifecycle processing failed", ex);
        }
    }
}
