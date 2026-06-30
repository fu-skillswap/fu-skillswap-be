package com.fptu.exe.skillswap.modules.booking.event;

import com.fptu.exe.skillswap.modules.mail.service.EmailService;
import com.fptu.exe.skillswap.modules.mail.template.HtmlEmailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEmailListener {

    private static final String PLATFORM_URL = HtmlEmailTemplate.PLATFORM_URL;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy");

    private final EmailService emailService;

    // Best-effort email must not hold the API response path after the business transaction commits.
    @Async("mailNotificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBookingEmailNotification(BookingEmailNotificationEvent event) {
        log.info("Processing email event: {} for booking: {}", event.getEventType(), event.getBookingId());

        EmailContent content = buildContent(event);
        try {
            emailService.sendHtmlEmail(
                    event.getRecipientEmail(),
                    content.subject(),
                    renderHtml(event, content),
                    renderPlainText(event, content)
            );
        } catch (Exception ex) {
            log.error("Booking email event failed but transaction remains committed. eventType={}, bookingId={}, recipient={}",
                    event.getEventType(), event.getBookingId(), event.getRecipientEmail(), ex);
        }
    }

    private EmailContent buildContent(BookingEmailNotificationEvent event) {
        boolean isFree = Boolean.TRUE.equals(event.getServiceFree());
        return switch (event.getEventType()) {
            case BOOKING_ACCEPTED_EMAIL -> isFree
                    ? new EmailContent(
                    "[SkillSwap] Lịch học miễn phí của bạn đã được xác nhận",
                    "Mentor đã chấp nhận yêu cầu đặt lịch của bạn",
                    "Lịch học miễn phí của bạn đã được mentor chấp nhận và hệ thống đã xác nhận lịch thành công.",
                    "Đã xác nhận",
                    "Chuẩn bị nội dung cho buổi học và truy cập phòng học đúng giờ.",
                    "Xem chi tiết lịch học"
            )
                    : new EmailContent(
                    "[SkillSwap] Mentor đã chấp nhận lịch của bạn",
                    "Mentor đã chấp nhận yêu cầu đặt lịch của bạn",
                    "Lịch mentoring của bạn đã được mentor chấp nhận. Vui lòng hoàn tất thanh toán trong vòng 2 giờ để hệ thống xác nhận lịch.",
                    "Chờ thanh toán",
                    "Hoàn tất thanh toán trong vòng 2 giờ để giữ lịch mentoring này.",
                    "Truy cập SkillSwap"
            );
            case BOOKING_PAID_CONFIRMED_EMAIL -> isFree
                    ? new EmailContent(
                    "[SkillSwap] Lịch học miễn phí của bạn đã được xác nhận",
                    "Lịch học miễn phí đã được xác nhận",
                    "Lịch mentoring miễn phí của bạn đã được xác nhận thành công.",
                    "Đã xác nhận",
                    "Hãy chuẩn bị nội dung cho buổi mentoring và theo dõi lịch trong SkillSwap.",
                    "Xem lịch mentoring"
            )
                    : new EmailContent(
                    "[SkillSwap] Lịch mentoring đã được thanh toán và hệ thống đã xác nhận lịch",
                    "Mentee đã hoàn tất thanh toán",
                    "Lịch mentoring đã được xác nhận sau khi hệ thống ghi nhận thanh toán thành công.",
                    "Đã xác nhận",
                    "Hãy chuẩn bị nội dung cho buổi mentoring và theo dõi lịch trong SkillSwap.",
                    "Xem lịch mentoring"
            );
            case BOOKING_REJECTED_EMAIL -> new EmailContent(
                    "[SkillSwap] Yêu cầu đặt lịch của bạn đã bị mentor từ chối",
                    "Yêu cầu đặt lịch chưa được chấp nhận",
                    "Mentor đã từ chối yêu cầu đặt lịch này. Bạn có thể chọn khung giờ khác hoặc tìm mentor phù hợp hơn.",
                    "Đã từ chối",
                    "Quay lại SkillSwap để chọn lịch hoặc mentor khác phù hợp với mục tiêu của bạn.",
                    "Tìm lịch khác"
            );
            case BOOKING_CANCELLED_BY_MENTEE_EMAIL -> new EmailContent(
                    "[SkillSwap] Mentee đã hủy lịch",
                    "Mentee đã hủy lịch mentoring",
                    "Lịch mentoring này đã được mentee hủy. Hệ thống đã cập nhật trạng thái lịch liên quan.",
                    "Đã hủy",
                    "Bạn có thể kiểm tra lại lịch trống và các yêu cầu đặt lịch khác trong SkillSwap.",
                    "Xem lịch của tôi"
            );
            case BOOKING_CANCELLED_BY_MENTOR_EMAIL -> new EmailContent(
                    "[SkillSwap] Mentor đã hủy lịch",
                    "Mentor đã hủy lịch mentoring",
                    "Lịch mentoring này đã được mentor hủy. Bạn có thể quay lại SkillSwap để đặt lịch khác.",
                    "Đã hủy",
                    "Chọn mentor hoặc khung giờ khác để tiếp tục mục tiêu mentoring của bạn.",
                    "Đặt lịch khác"
            );
        };
    }

    private String renderHtml(BookingEmailNotificationEvent event, EmailContent content) {
        String actorName = defaultText(event.getActorName(), "SkillSwap");
        String serviceTitle = defaultText(event.getServiceTitle(), "Buổi mentoring cá nhân");
        String detailRows = detailRow("Mã booking", escape(shortBookingId(event)))
                + detailRow("Dịch vụ", escape(serviceTitle))
                + detailRow("Người liên quan", escape(actorName))
                + detailRow("Thời gian", escape(formatSchedule(event)))
                + (event.getCreatedAt() == null ? "" : detailRow("Ngày tạo yêu cầu", escape(formatDateTime(event.getCreatedAt()))))
                + detailRow("Thời lượng", escape(formatDuration(event.getServiceDurationMinutes())))
                + detailRow("Chi phí", escape(formatPrice(event)))
                + detailRow("Mục tiêu", escape(defaultText(event.getLearningGoalTitle(), "Mục tiêu mentoring")))
                + detailRow("Mô tả", escape(defaultText(event.getLearningGoalDescription(), "Chưa có mô tả chi tiết")))
                + detailRow("Kết quả kỳ vọng", escape(defaultText(event.getServiceExpectedOutcome(), "Mentor sẽ hỗ trợ theo mục tiêu đã đăng ký.")))
                + (trimToNull(event.getMeetingLink()) == null ? "" : detailRow("Link học", HtmlEmailTemplate.safeLink(event.getMeetingLink())))
                + (event.getPaymentDeadline() == null ? "" : detailRow("Hạn thanh toán", escape(formatDateTime(event.getPaymentDeadline()))))
                + (trimToNull(event.getMentorResponseNote()) == null ? "" : detailRow("Ghi chú từ mentor", escape(event.getMentorResponseNote())))
                + (trimToNull(event.getReason()) == null ? "" : detailRow("Lý do", escape(event.getReason())));

        return HtmlEmailTemplate.render(new HtmlEmailTemplate.Model(
                content.subject(),
                content.summary(),
                content.statusLabel(),
                content.heading(),
                content.summary(),
                "Mentoring",
                serviceTitle,
                defaultText(event.getRecipientName(), "bạn"),
                buildIntro(event, content, actorName),
                detailRows,
                content.nextStep(),
                content.ctaLabel(),
                PLATFORM_URL
        ));
    }

    private String renderPlainText(BookingEmailNotificationEvent event, EmailContent content) {
        StringBuilder builder = new StringBuilder();
        builder.append(content.heading()).append("\n\n");
        builder.append("Xin chào ").append(defaultText(event.getRecipientName(), "bạn")).append(",\n\n");
        builder.append(content.summary()).append("\n\n");
        builder.append("Mã booking: ").append(shortBookingId(event)).append("\n");
        builder.append("Dịch vụ: ").append(defaultText(event.getServiceTitle(), "Buổi mentoring cá nhân")).append("\n");
        builder.append("Thời gian: ").append(formatSchedule(event)).append("\n");
        builder.append("Thời lượng: ").append(formatDuration(event.getServiceDurationMinutes())).append("\n");
        builder.append("Chi phí: ").append(formatPrice(event)).append("\n");
        builder.append("Mục tiêu: ").append(defaultText(event.getLearningGoalTitle(), "Mục tiêu mentoring")).append("\n");
        if (event.getCreatedAt() != null) {
            builder.append("Ngày tạo yêu cầu: ").append(formatDateTime(event.getCreatedAt())).append("\n");
        }
        if (event.getPaymentDeadline() != null) {
            builder.append("Hạn thanh toán: ").append(formatDateTime(event.getPaymentDeadline())).append("\n");
        }
        if (trimToNull(event.getMeetingLink()) != null) {
            builder.append("Link học: ").append(event.getMeetingLink()).append("\n");
        }
        if (trimToNull(event.getReason()) != null) {
            builder.append("Lý do: ").append(event.getReason()).append("\n");
        }
        if (trimToNull(event.getMentorResponseNote()) != null) {
            builder.append("Ghi chú từ mentor: ").append(event.getMentorResponseNote()).append("\n");
        }
        builder.append("\nBước tiếp theo: ").append(content.nextStep()).append("\n");
        builder.append("Truy cập SkillSwap: ").append(PLATFORM_URL).append("\n");
        return builder.toString();
    }

    private String buildIntro(BookingEmailNotificationEvent event, EmailContent content, String actorName) {
        boolean isFree = Boolean.TRUE.equals(event.getServiceFree());
        if (event.getEventType() == BookingEmailNotificationEvent.EventType.BOOKING_ACCEPTED_EMAIL) {
            return isFree
                    ? "Mentor " + actorName + " đã chấp nhận yêu cầu học miễn phí của bạn. Lịch học đã được xác nhận thành công."
                    : actorName + " đã chấp nhận yêu cầu mentoring của bạn. SkillSwap đã giữ lịch tạm thời để bạn hoàn tất thanh toán.";
        }
        if (event.getEventType() == BookingEmailNotificationEvent.EventType.BOOKING_PAID_CONFIRMED_EMAIL) {
            return isFree
                    ? "Lịch học miễn phí với " + actorName + " đã được xác nhận thành công."
                    : "Mentee " + actorName + " đã hoàn tất thanh toán. Lịch mentoring đã được xác nhận trên hệ thống.";
        }
        return content.summary();
    }

    private String detailRow(String label, String value) {
        return HtmlEmailTemplate.detailRow(label, value);
    }

    private String formatSchedule(BookingEmailNotificationEvent event) {
        if (event.getBookingStartTime() == null || event.getBookingEndTime() == null) {
            return "Chưa xác định";
        }
        return formatDateTime(event.getBookingStartTime()) + " - " + formatDateTime(event.getBookingEndTime());
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "Chưa xác định" : value.format(DATE_TIME_FORMATTER);
    }

    private String formatDuration(Integer durationMinutes) {
        return durationMinutes == null || durationMinutes <= 0 ? "Theo dịch vụ" : durationMinutes + " phút";
    }

    private String formatPrice(BookingEmailNotificationEvent event) {
        if (Boolean.TRUE.equals(event.getServiceFree())) {
            return "Miễn phí";
        }
        Integer price = event.getServicePriceScoin();
        return price == null || price <= 0 ? "Miễn phí" : price + " Scoin";
    }

    private String shortBookingId(BookingEmailNotificationEvent event) {
        if (event.getBookingId() == null) {
            return "N/A";
        }
        String value = event.getBookingId().toString();
        return value.length() <= 8 ? value : value.substring(0, 8).toUpperCase();
    }

    private String defaultText(String value, String fallback) {
        return HtmlEmailTemplate.defaultText(value, fallback);
    }

    private String trimToNull(String value) {
        return HtmlEmailTemplate.trimToNull(value);
    }

    private String escape(String value) {
        return HtmlEmailTemplate.escape(value);
    }

    private record EmailContent(
            String subject,
            String heading,
            String summary,
            String statusLabel,
            String nextStep,
            String ctaLabel
    ) {
    }
}
