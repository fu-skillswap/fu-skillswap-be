package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.identity.event.CalendarSyncAbortedNearStartTimeEvent;
import com.fptu.exe.skillswap.modules.identity.event.CalendarSyncConnectionRevokedEvent;
import com.fptu.exe.skillswap.modules.identity.event.CalendarSyncFailedEvent;
import com.fptu.exe.skillswap.modules.mail.service.EmailDispatchService;
import com.fptu.exe.skillswap.modules.mail.template.HtmlEmailTemplate;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarFallbackNotificationListener {

    private final ApplicationEventPublisher eventPublisher;
    private final BookingRepository bookingRepository;
    private final EmailDispatchService emailDispatchService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSyncFailed(CalendarSyncFailedEvent event) {
        publishNotification(event.bookingId(), event.mentorUserId(), event.menteeUserId(),
                "Google Calendar tạm thời chưa đồng bộ được",
                "Hệ thống chưa thể đồng bộ buổi mentoring lên Google Calendar. Bạn vẫn có thể vào app để xem lịch và link họp.");
        sendEmailBestEffort(event.bookingId(), event.errorMessage());
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSyncAborted(CalendarSyncAbortedNearStartTimeEvent event) {
        publishNotification(event.bookingId(), event.mentorUserId(), event.menteeUserId(),
                "Đồng bộ lịch tự động đã dừng do quá sát giờ học",
                "Buổi mentoring đã quá sát giờ bắt đầu nên hệ thống dừng retry tạo lịch tự động. Hãy kiểm tra lại trong app.");
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConnectionRevoked(CalendarSyncConnectionRevokedEvent event) {
        publishNotification(event.bookingId(), event.mentorUserId(), event.menteeUserId(),
                "Mentor đã ngắt kết nối Google Calendar",
                "Các thay đổi lịch tiếp theo sẽ không còn tự đồng bộ lên Google Calendar cho booking này.");
    }

    private void publishNotification(java.util.UUID bookingId,
                                     java.util.UUID mentorUserId,
                                     java.util.UUID menteeUserId,
                                     String title,
                                     String message) {
        eventPublisher.publishEvent(new NotificationEvent(
                mentorUserId,
                NotificationType.GOOGLE_CALENDAR_SYNC_NOTICE,
                title,
                message,
                "BOOKING",
                bookingId
        ));
        eventPublisher.publishEvent(new NotificationEvent(
                menteeUserId,
                NotificationType.GOOGLE_CALENDAR_SYNC_NOTICE,
                title,
                message,
                "BOOKING",
                bookingId
        ));
    }

    private void sendEmailBestEffort(java.util.UUID bookingId, String errorMessage) {
        bookingRepository.findByIdForSessionUpdate(bookingId).ifPresent(booking -> {
            String subject = "[SkillSwap] Google Calendar chưa đồng bộ được cho buổi mentoring";
            String summary = "Hệ thống chưa thể tự đồng bộ lịch lên Google Calendar, nhưng booking của bạn trên SkillSwap vẫn còn hiệu lực.";
            String detailRows = HtmlEmailTemplate.detailRow("Mã booking", HtmlEmailTemplate.escape(bookingId.toString()))
                    + HtmlEmailTemplate.detailRow("Mục tiêu", HtmlEmailTemplate.escape(HtmlEmailTemplate.defaultText(booking.getLearningGoalTitle(), "Mentoring session")))
                    + HtmlEmailTemplate.detailRow("Chi tiết lỗi", HtmlEmailTemplate.escape(HtmlEmailTemplate.defaultText(errorMessage, "Google Calendar tạm thời không phản hồi đúng.")));
            String html = HtmlEmailTemplate.render(new HtmlEmailTemplate.Model(
                    subject,
                    summary,
                    "Cần kiểm tra lại",
                    "Google Calendar chưa đồng bộ được",
                    summary,
                    "Booking",
                    HtmlEmailTemplate.defaultText(booking.getLearningGoalTitle(), "Mentoring session"),
                    "bạn",
                    summary,
                    detailRows,
                    "Vui lòng mở app SkillSwap để kiểm tra lịch và link học hiện tại.",
                    "Mở SkillSwap",
                    HtmlEmailTemplate.PLATFORM_URL
            ));
            String plain = subject + "\n\n" + summary + "\nBooking: " + bookingId;
            try {
                emailDispatchService.sendHtmlOnce(
                        "GCALENDAR_FALLBACK:" + bookingId + ":" + booking.getMentee().getEmail(),
                        booking.getMentee().getEmail(),
                        subject,
                        html,
                        plain,
                        "GOOGLE_CALENDAR_SYNC_FALLBACK"
                );
                emailDispatchService.sendHtmlOnce(
                        "GCALENDAR_FALLBACK:" + bookingId + ":" + booking.getMentorProfile().getUser().getEmail(),
                        booking.getMentorProfile().getUser().getEmail(),
                        subject,
                        html,
                        plain,
                        "GOOGLE_CALENDAR_SYNC_FALLBACK"
                );
            } catch (Exception ex) {
                log.warn("Google calendar fallback email failed for booking {}", bookingId, ex);
            }
        });
    }
}
