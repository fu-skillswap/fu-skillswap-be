package com.fptu.exe.skillswap.modules.booking.scheduler;

import com.fptu.exe.skillswap.modules.booking.service.BookingRescheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingRescheduleScheduler {

    private final BookingRescheduleService bookingRescheduleService;

    @Scheduled(cron = "0 */15 * * * *")
    public void expirePendingRescheduleRequests() {
        try {
            int expired = bookingRescheduleService.expirePendingRequests();
            if (expired > 0) {
                log.info("Expired {} stale booking reschedule requests.", expired);
            }
        } catch (Exception ex) {
            log.error("Error occurred while expiring stale booking reschedule requests", ex);
        }
    }
}
