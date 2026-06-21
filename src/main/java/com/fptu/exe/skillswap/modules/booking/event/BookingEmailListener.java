package com.fptu.exe.skillswap.modules.booking.event;

import com.fptu.exe.skillswap.modules.mail.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEmailListener {

    private final EmailService emailService;

    // Use Async if mail sending is slow. In this MVP, running after commit is enough to separate transaction boundaries, 
    // but executing synchronously still blocks the client response until mail sends. 
    // For pure Best-Effort without blocking API, @Async is recommended, but let's keep it simple first.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBookingEmailNotification(BookingEmailNotificationEvent event) {
        log.info("Processing email event: {} for booking: {}", event.getEventType(), event.getBookingId());

        String subject = "";
        String body = "";

        switch (event.getEventType()) {
            case BOOKING_ACCEPTED_EMAIL:
                subject = "[SkillSwap] Lịch mentoring của bạn đã được chấp nhận";
                body = String.format("""
                        Xin chào %s,

                        %s đã chấp nhận lịch mentoring của bạn.

                        Thời gian: %s - %s
                        """, 
                        event.getRecipientName(), 
                        event.getActorName(),
                        event.getBookingStartTime(),
                        event.getBookingEndTime());
                
                if (event.getMeetingLink() != null && !event.getMeetingLink().isBlank()) {
                    body += String.format("Link học: %s\n\n", event.getMeetingLink());
                } else {
                    body += "\n";
                }
                body += "Vui lòng mở SkillSwap để xem chi tiết lịch học.\n\nTrân trọng,\nSkillSwap";
                break;

            case BOOKING_REJECTED_EMAIL:
                subject = "[SkillSwap] Yêu cầu đặt lịch của bạn đã bị từ chối";
                body = String.format("""
                        Xin chào %s,

                        %s đã từ chối yêu cầu đặt lịch mentoring của bạn.

                        """, 
                        event.getRecipientName(), 
                        event.getActorName());
                
                if (event.getReason() != null && !event.getReason().isBlank()) {
                    body += String.format("Lý do: %s\n\n", event.getReason());
                }
                body += "Bạn có thể chọn khung giờ khác hoặc tìm mentor phù hợp khác trên SkillSwap.\n\nTrân trọng,\nSkillSwap";
                break;

            case BOOKING_CANCELLED_BY_MENTEE_EMAIL:
                subject = "[SkillSwap] Mentee đã hủy lịch mentoring";
                body = String.format("""
                        Xin chào %s,

                        %s đã hủy lịch mentoring.

                        Thời gian: %s - %s
                        """, 
                        event.getRecipientName(), 
                        event.getActorName(),
                        event.getBookingStartTime(),
                        event.getBookingEndTime());
                
                if (event.getReason() != null && !event.getReason().isBlank()) {
                    body += String.format("Lý do: %s\n\n", event.getReason());
                } else {
                    body += "\n";
                }
                body += "Vui lòng mở SkillSwap để xem chi tiết.\n\nTrân trọng,\nSkillSwap";
                break;

            case BOOKING_CANCELLED_BY_MENTOR_EMAIL:
                subject = "[SkillSwap] Mentor đã hủy lịch mentoring";
                body = String.format("""
                        Xin chào %s,

                        %s đã hủy lịch mentoring.

                        Thời gian: %s - %s
                        """, 
                        event.getRecipientName(), 
                        event.getActorName(),
                        event.getBookingStartTime(),
                        event.getBookingEndTime());
                
                if (event.getReason() != null && !event.getReason().isBlank()) {
                    body += String.format("Lý do: %s\n\n", event.getReason());
                } else {
                    body += "\n";
                }
                body += "Bạn có thể đặt lại lịch hoặc chọn mentor khác trên SkillSwap.\n\nTrân trọng,\nSkillSwap";
                break;
        }

        emailService.sendSimpleEmail(event.getRecipientEmail(), subject, body);
    }
}
