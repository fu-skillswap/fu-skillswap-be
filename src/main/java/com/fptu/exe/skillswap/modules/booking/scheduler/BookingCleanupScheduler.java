package com.fptu.exe.skillswap.modules.booking.scheduler;

import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCleanupScheduler {

    private final BookingService bookingService;

    @Scheduled(cron = "0 */30 * * * *") // Runs every 30 minutes
    public void expirePendingBookings() {
        log.info("Starting scheduled job to expire stale pending bookings...");
        try {
            int expiredCount = bookingService.expireStalePendingBookings();
            if (expiredCount > 0) {
                log.info("Expired {} stale pending bookings.", expiredCount);
            } else {
                log.debug("No stale pending bookings to expire.");
            }
        } catch (Exception ex) {
            log.error("Error occurred while expiring stale pending bookings", ex);
        }
    }
}
