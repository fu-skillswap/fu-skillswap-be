package com.fptu.exe.skillswap.modules.booking.event;

import com.fptu.exe.skillswap.modules.mail.service.EmailService;
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

    private static final String PLATFORM_URL = "https://skillswap.asia";
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
        return switch (event.getEventType()) {
            case BOOKING_ACCEPTED_EMAIL -> new EmailContent(
                    "[SkillSwap] Mentor đã chấp nhận lịch",
                    "Mentor đã chấp nhận yêu cầu",
                    "Lịch mentoring của bạn đã được mentor chấp nhận. Vui lòng hoàn tất thanh toán trong vòng 2 giờ để hệ thống xác nhận lịch.",
                    "Chờ thanh toán",
                    "Hoàn tất thanh toán trong vòng 2 giờ để giữ lịch mentoring này.",
                    "Truy cập SkillSwap"
            );
            case BOOKING_PAID_CONFIRMED_EMAIL -> new EmailContent(
                    "[SkillSwap] Lịch mentoring đã xác nhận",
                    "Mentee đã hoàn tất thanh toán",
                    "Lịch mentoring đã được xác nhận sau khi hệ thống ghi nhận thanh toán thành công.",
                    "Đã xác nhận",
                    "Chuẩn bị nội dung mentoring và theo dõi lịch trong SkillSwap.",
                    "Xem lịch mentoring"
            );
            case BOOKING_REJECTED_EMAIL -> new EmailContent(
                    "[SkillSwap] Yêu cầu đặt lịch bị từ chối",
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
        String recipientName = escape(defaultText(event.getRecipientName(), "bạn"));
        String actorName = escape(defaultText(event.getActorName(), "SkillSwap"));
        String serviceTitle = escape(defaultText(event.getServiceTitle(), "Buổi mentoring cá nhân"));
        String goalTitle = escape(defaultText(event.getLearningGoalTitle(), "Mục tiêu mentoring"));
        String goalDescription = escape(defaultText(event.getLearningGoalDescription(), "Chưa có mô tả chi tiết"));
        String expectedOutcome = escape(defaultText(event.getServiceExpectedOutcome(), "Mentor sẽ hỗ trợ theo mục tiêu đã đăng ký."));
        String mentorNote = trimToNull(event.getMentorResponseNote()) == null ? "" : detailRow("Ghi chú từ mentor", escape(event.getMentorResponseNote()));
        String reason = trimToNull(event.getReason()) == null ? "" : detailRow("Lý do", escape(event.getReason()));
        String paymentDeadline = event.getPaymentDeadline() == null ? "" : detailRow("Hạn thanh toán", escape(formatDateTime(event.getPaymentDeadline())));
        String createdAt = event.getCreatedAt() == null ? "" : detailRow("Ngày tạo yêu cầu", escape(formatDateTime(event.getCreatedAt())));
        String meetingLink = trimToNull(event.getMeetingLink()) == null ? "" : detailRow("Link học", safeLink(event.getMeetingLink()));

        String html = """
                <!doctype html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f2f7fb;font-family:Arial,Helvetica,sans-serif;color:#102a43;">
                  <div style="display:none;max-height:0;overflow:hidden;color:#f2f7fb;">%s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f2f7fb;padding:28px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="640" cellspacing="0" cellpadding="0" style="max-width:640px;width:100%%;background:#ffffff;border-radius:28px;overflow:hidden;border:1px solid #d4e3ef;">
                          <tr>
                            <td style="padding:24px 34px;background:#f8fbff;text-align:center;border-bottom:1px solid #dbe8f3;">
                              <div style="font-size:34px;line-height:1;font-weight:800;color:#0b3a67;letter-spacing:-1px;">SkillSwap</div>
                              <div style="margin-top:8px;font-size:13px;color:#5d7083;">Nền tảng mentoring và trao đổi kỹ năng FPTU</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="background:#d7eaf8;padding:0;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                                <tr>
                                  <td style="padding:34px 30px 30px 34px;width:56%%;vertical-align:top;">
                                    <div style="display:inline-block;padding:7px 12px;border-radius:999px;background:#ffffff;color:#0b3a67;font-size:12px;font-weight:700;">%s</div>
                                    <h1 style="margin:18px 0 0;font-size:30px;line-height:1.15;color:#082f57;letter-spacing:-0.6px;">%s</h1>
                                    <p style="margin:14px 0 0;font-size:15px;line-height:1.6;color:#26465f;">%s</p>
                                  </td>
                                  <td style="padding:28px 30px 28px 0;vertical-align:middle;">
                                    <div style="background:#ffffff;border-radius:24px;padding:18px;border:1px solid #b9d6ea;box-shadow:0 10px 24px rgba(11,58,103,0.16);">
                                      <div style="height:12px;width:86px;background:#0b3a67;border-radius:999px;margin-bottom:14px;"></div>
                                      <div style="padding:12px;border-radius:16px;background:#f6faff;border:1px solid #dbe8f3;margin-bottom:10px;">
                                        <div style="font-size:12px;color:#5d7083;">Mentoring</div>
                                        <div style="font-size:15px;font-weight:700;color:#102a43;">%s</div>
                                      </div>
                                      <table role="presentation" cellspacing="0" cellpadding="0" style="width:100%%;">
                                        <tr>
                                          <td style="padding-right:8px;"><span style="display:block;width:54px;height:42px;border-radius:14px;background:#b9d6ea;"></span></td>
                                          <td style="padding-right:8px;"><span style="display:block;width:54px;height:42px;border-radius:14px;background:#7fb3d5;"></span></td>
                                          <td><span style="display:block;width:54px;height:42px;border-radius:14px;background:#0b3a67;"></span></td>
                                        </tr>
                                      </table>
                                    </div>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:34px;">
                              <p style="margin:0 0 14px;font-size:17px;line-height:1.6;">Xin chào <strong>%s</strong>,</p>
                              <p style="margin:0 0 24px;font-size:15px;line-height:1.7;color:#314b61;">%s</p>
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border:1px solid #d4e3ef;border-radius:20px;overflow:hidden;margin-bottom:22px;">
                                %s
                                %s
                                %s
                                %s
                                %s
                                %s
                                %s
                                %s
                                %s
                                %s
                                %s
                                %s
                              </table>
                              <div style="background:#eef7ff;border:1px solid #b9d6ea;border-radius:18px;padding:18px 20px;margin:0 0 24px;">
                                <div style="font-size:13px;font-weight:800;color:#0b3a67;text-transform:uppercase;letter-spacing:.4px;">Bước tiếp theo</div>
                                <div style="margin-top:8px;font-size:15px;line-height:1.6;color:#173b59;">%s</div>
                              </div>
                              <div style="text-align:center;margin:30px 0 18px;">
                                <a href="%s" style="display:inline-block;background:#0b3a67;color:#ffffff;text-decoration:none;font-weight:800;border-radius:999px;padding:14px 28px;font-size:15px;">%s</a>
                              </div>
                              <p style="margin:18px 0 0;font-size:13px;line-height:1.6;color:#697f91;text-align:center;">Link web: <a href="%s" style="color:#0b3a67;">%s</a></p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:22px 34px;background:#062b4f;color:#d7eaf8;text-align:center;">
                              <div style="font-size:14px;font-weight:800;color:#ffffff;">SkillSwap</div>
                              <div style="margin-top:8px;font-size:12px;line-height:1.6;">Email tự động từ hệ thống. Vui lòng không trả lời trực tiếp email này.</div>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """;

        return String.format(html,
                escape(content.subject()),
                escape(content.summary()),
                escape(content.statusLabel()),
                escape(content.heading()),
                escape(content.summary()),
                serviceTitle,
                recipientName,
                escape(buildIntro(event, content, actorName)),
                detailRow("Mã booking", escape(shortBookingId(event))),
                detailRow("Dịch vụ", serviceTitle),
                detailRow("Người liên quan", actorName),
                detailRow("Thời gian", escape(formatSchedule(event))),
                createdAt,
                detailRow("Thời lượng", escape(formatDuration(event.getServiceDurationMinutes()))),
                detailRow("Chi phí", escape(formatPrice(event))),
                detailRow("Mục tiêu", goalTitle),
                detailRow("Mô tả", goalDescription),
                detailRow("Kết quả kỳ vọng", expectedOutcome),
                meetingLink,
                paymentDeadline + mentorNote + reason,
                escape(content.nextStep()),
                PLATFORM_URL,
                escape(content.ctaLabel()),
                PLATFORM_URL,
                PLATFORM_URL
        );
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
        if (event.getEventType() == BookingEmailNotificationEvent.EventType.BOOKING_ACCEPTED_EMAIL) {
            return actorName + " đã chấp nhận yêu cầu mentoring của bạn. SkillSwap đã giữ lịch tạm thời để bạn hoàn tất thanh toán.";
        }
        if (event.getEventType() == BookingEmailNotificationEvent.EventType.BOOKING_PAID_CONFIRMED_EMAIL) {
            return "Mentee " + actorName + " đã hoàn tất thanh toán. Lịch mentoring đã được xác nhận trên hệ thống.";
        }
        return content.summary();
    }

    private String detailRow(String label, String value) {
        return """
                <tr>
                  <td style="padding:13px 16px;background:#f6faff;border-bottom:1px solid #d4e3ef;width:34%%;font-size:13px;font-weight:800;color:#526a80;">%s</td>
                  <td style="padding:13px 16px;border-bottom:1px solid #d4e3ef;font-size:14px;line-height:1.55;color:#102a43;">%s</td>
                </tr>
                """.formatted(label, value);
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
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String safeLink(String url) {
        String trimmed = trimToNull(url);
        if (trimmed == null) {
            return "";
        }
        String escaped = escape(trimmed);
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return escaped;
        }
        return "<a href=\"" + escaped + "\" style=\"color:#0b3a67;text-decoration:none;font-weight:700;\">" + escaped + "</a>";
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
