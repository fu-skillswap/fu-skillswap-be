package com.fptu.exe.skillswap.modules.booking.scheduler;

import com.fptu.exe.skillswap.modules.booking.service.BookingReminderEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEmailReminderScheduler {

    private final BookingReminderEmailService bookingReminderEmailService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Ho_Chi_Minh")
    public void sendUpcomingSessionReminders() {
        try {
            int sent = bookingReminderEmailService.sendUpcomingSessionReminders();
            if (sent > 0) {
                log.info("Sent {} upcoming booking reminder emails.", sent);
            }
        } catch (Exception ex) {
            log.error("Error occurred while sending upcoming booking reminder emails", ex);
        }
    }

    @Scheduled(cron = "0 0 5,11,17,21 * * *", zone = "Asia/Ho_Chi_Minh")
    public void sendPendingRequestDigests() {
        try {
            int sent = bookingReminderEmailService.sendPendingRequestDigests();
            if (sent > 0) {
                log.info("Sent {} mentor pending request digest emails.", sent);
            }
        } catch (Exception ex) {
            log.error("Error occurred while sending mentor pending request digest emails", ex);
        }
    }
}
