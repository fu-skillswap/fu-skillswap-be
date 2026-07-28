package com.fptu.exe.skillswap.modules.booking.scheduler;

import com.fptu.exe.skillswap.modules.booking.service.BookingLifecycleMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "application.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class BookingCleanupScheduler {

    private final BookingLifecycleMaintenanceService bookingLifecycleMaintenanceService;

    // Indexed deadline scans only; five minutes bounds stale UI without burdening the single VPS.
    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void expirePendingBookings() {
        log.info("Starting scheduled job to expire stale pending bookings...");
        try {
            int expiredCount = bookingLifecycleMaintenanceService.expireStalePendingBookings();
            if (expiredCount > 0) {
                log.info("Expired {} stale pending bookings.", expiredCount);
            } else {
                log.debug("No stale pending bookings to expire.");
            }
            int paymentExpiredCount = bookingLifecycleMaintenanceService.expireAwaitingPaymentBookings();
            if (paymentExpiredCount > 0) {
                log.info("Expired {} stale awaiting-payment bookings.", paymentExpiredCount);
            } else {
                log.debug("No stale awaiting-payment bookings to expire.");
            }
        } catch (Exception ex) {
            log.error("Error occurred while expiring stale pending bookings", ex);
        }
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void processPostSessionLifecycle() {
        try {
            int changed = bookingLifecycleMaintenanceService.processPostSessionLifecycle();
            if (changed > 0) {
                log.info("Processed {} post-session booking lifecycle transitions/reminders.", changed);
            }
        } catch (Exception ex) {
            log.error("Post-session booking lifecycle job failed", ex);
        }
    }
}
