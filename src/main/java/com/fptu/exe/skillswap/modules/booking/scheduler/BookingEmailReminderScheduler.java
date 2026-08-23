package com.fptu.exe.skillswap.modules.booking.scheduler;

import com.fptu.exe.skillswap.modules.booking.service.BookingReminderEmailService;
import com.fptu.exe.skillswap.modules.booking.service.BookingEngagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "application.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class BookingEmailReminderScheduler {

    private final BookingReminderEmailService bookingReminderEmailService;
    private final BookingEngagementService bookingEngagementService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Ho_Chi_Minh")
    public void sendUpcomingSessionReminders() {
        try {
            int sent = bookingReminderEmailService.sendUpcomingSessionReminders();
            int inAppSent = bookingEngagementService.sendScheduledReminders();
            if (sent > 0) {
                log.info("Sent {} upcoming booking reminder emails.", sent);
            }
            if (inAppSent > 0) log.info("Sent {} in-app booking engagement reminders.", inAppSent);
            int warningSent = bookingReminderEmailService.sendAutoCloseWarningEmails();
            warningSent += bookingReminderEmailService.sendMeetingAccessFallbackWarnings();
            if (warningSent > 0) {
                log.info("Sent {} auto-close warning emails.", warningSent);
            }
        } catch (Exception ex) {
            log.error("Error occurred while sending upcoming booking reminder emails or auto-close warnings", ex);
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

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")
    public void sendDailyMentorScheduleDigests() {
        try {
            int sent = bookingReminderEmailService.sendDailyMentorScheduleDigests();
            if (sent > 0) {
                log.info("Sent {} daily mentor schedule digest emails.", sent);
            }
        } catch (Exception ex) {
            log.error("Error occurred while sending daily mentor schedule digest emails", ex);
        }
    }
}
