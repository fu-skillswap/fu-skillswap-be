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

    private static final String PLATFORM_URL = "https://skillswap.asia";

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
                subject = "[SkillSwap] Mentor đã chấp nhận yêu cầu đặt lịch của bạn";
                body = String.format("""
                        Kính gửi %s,

                        Mentor %s đã chấp nhận yêu cầu đặt lịch mentoring của bạn.

                        Thời gian dự kiến: %s - %s
                        Hạn hoàn tất thanh toán: %s

                        Vui lòng hoàn tất thanh toán trong vòng 2 giờ kể từ thời điểm mentor chấp nhận. Nếu quá thời hạn này mà hệ thống chưa ghi nhận thanh toán thành công, yêu cầu đặt lịch sẽ tự động bị hủy.

                        Truy cập nền tảng: %s

                        Trân trọng,
                        Đội ngũ SkillSwap
                        """,
                        event.getRecipientName(),
                        event.getActorName(),
                        event.getBookingStartTime(),
                        event.getBookingEndTime(),
                        event.getPaymentDeadline(),
                        PLATFORM_URL);
                break;

            case BOOKING_PAID_CONFIRMED_EMAIL:
                subject = "[SkillSwap] Lịch mentoring đã được mentee thanh toán và xác nhận";
                body = String.format("""
                        Kính gửi %s,

                        Mentee đã hoàn tất thanh toán cho lịch mentoring với bạn.

                        Thời gian đã xác nhận: %s - %s

                        Bạn có thể truy cập nền tảng để chuẩn bị buổi mentoring và theo dõi các thông tin liên quan.

                        Truy cập nền tảng: %s

                        Trân trọng,
                        Đội ngũ SkillSwap
                        """,
                        event.getRecipientName(),
                        event.getBookingStartTime(),
                        event.getBookingEndTime(),
                        PLATFORM_URL);
                break;

            case BOOKING_REJECTED_EMAIL:
                subject = "[SkillSwap] Yêu cầu đặt lịch của bạn đã bị từ chối";
                body = String.format("""
                        Kính gửi %s,

                        Mentor %s đã từ chối yêu cầu đặt lịch mentoring của bạn.

                        """,
                        event.getRecipientName(),
                        event.getActorName());

                if (event.getReason() != null && !event.getReason().isBlank()) {
                    body += String.format("Lý do: %s\n\n", event.getReason());
                }
                body += "Bạn có thể quay lại nền tảng để chọn khung giờ khác hoặc tìm mentor phù hợp hơn.\n\n";
                body += "Truy cập nền tảng: " + PLATFORM_URL + "\n\nTrân trọng,\nĐội ngũ SkillSwap";
                break;

            case BOOKING_CANCELLED_BY_MENTEE_EMAIL:
                subject = "[SkillSwap] Mentee đã hủy lịch mentoring";
                body = String.format("""
                        Kính gửi %s,

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
                body += "Truy cập nền tảng: " + PLATFORM_URL + "\n\nTrân trọng,\nĐội ngũ SkillSwap";
                break;

            case BOOKING_CANCELLED_BY_MENTOR_EMAIL:
                subject = "[SkillSwap] Mentor đã hủy lịch mentoring";
                body = String.format("""
                        Kính gửi %s,

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
                body += "Truy cập nền tảng: " + PLATFORM_URL + "\n\nTrân trọng,\nĐội ngũ SkillSwap";
                break;
        }

        emailService.sendSimpleEmail(event.getRecipientEmail(), subject, body);
    }
}
