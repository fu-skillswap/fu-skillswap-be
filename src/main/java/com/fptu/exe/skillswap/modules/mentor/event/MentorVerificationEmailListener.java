package com.fptu.exe.skillswap.modules.mentor.event;

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
public class MentorVerificationEmailListener {

    private static final String PLATFORM_URL = HtmlEmailTemplate.PLATFORM_URL;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy");

    private final EmailService emailService;

    @Async("mailNotificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMentorVerificationEmail(MentorVerificationEmailNotificationEvent event) {
        log.info("Processing mentor verification email event: {} for request: {}",
                event.getEventType(), event.getRequestId());

        EmailContent content = buildContent(event);
        try {
            emailService.sendHtmlEmail(
                    event.getRecipientEmail(),
                    content.subject(),
                    renderHtml(event, content),
                    renderPlainText(event, content)
            );
        } catch (Exception ex) {
            log.error("Mentor verification email failed but transaction remains committed. eventType={}, requestId={}, recipient={}",
                    event.getEventType(), event.getRequestId(), event.getRecipientEmail(), ex);
        }
    }

    private EmailContent buildContent(MentorVerificationEmailNotificationEvent event) {
        return switch (event.getEventType()) {
            case APPROVED_EMAIL -> new EmailContent(
                    "[SkillSwap] Hồ sơ mentor của bạn đã được duyệt",
                    "Hồ sơ mentor của bạn đã được duyệt",
                    "Chúc mừng, hồ sơ mentor của bạn đã được xét duyệt thành công. Bạn có thể bắt đầu hoàn thiện dịch vụ, lịch khả dụng và nhận yêu cầu mentoring từ mentee.",
                    "Đã duyệt",
                    "Truy cập SkillSwap để kiểm tra hồ sơ mentor, cập nhật dịch vụ mentoring và tạo lịch khả dụng phù hợp để mentee có thể đặt lịch của bạn.",
                    "Mở SkillSwap"
            );
            case NEEDS_REVISION_EMAIL -> new EmailContent(
                    "[SkillSwap] Cần bổ sung hồ sơ mentor",
                    "Hồ sơ mentor cần được bổ sung",
                    "Hồ sơ mentor của bạn cần được cập nhật thêm thông tin trước khi SkillSwap có thể hoàn tất xét duyệt.",
                    "Cần bổ sung",
                    "Vui lòng xem ghi chú từ đội ngũ xét duyệt, cập nhật hồ sơ hoặc minh chứng cần thiết, sau đó gửi lại hồ sơ để được review.",
                    "Cập nhật hồ sơ"
            );
        };
    }

    private String renderHtml(MentorVerificationEmailNotificationEvent event, EmailContent content) {
        String reviewerName = defaultText(event.getReviewerName(), "Đội ngũ SkillSwap");
        String detailRows = detailRow("Mã hồ sơ", escape(shortRequestId(event)))
                + detailRow("Người xét duyệt", escape(reviewerName))
                + (event.getSubmittedAt() == null ? "" : detailRow("Ngày gửi hồ sơ", escape(formatDateTime(event.getSubmittedAt()))))
                + (event.getReviewedAt() == null ? "" : detailRow("Ngày xét duyệt", escape(formatDateTime(event.getReviewedAt()))))
                + (trimToNull(event.getReviewNote()) == null ? "" : detailRow("Ghi chú xét duyệt", escape(event.getReviewNote())));

        return HtmlEmailTemplate.render(new HtmlEmailTemplate.Model(
                content.subject(),
                content.summary(),
                content.statusLabel(),
                content.heading(),
                content.summary(),
                "Mentor Verification",
                content.heading(),
                defaultText(event.getRecipientName(), "bạn"),
                content.summary(),
                detailRows,
                content.nextStep(),
                content.ctaLabel(),
                PLATFORM_URL
        ));
    }

    private String renderPlainText(MentorVerificationEmailNotificationEvent event, EmailContent content) {
        StringBuilder builder = new StringBuilder();
        builder.append(content.heading()).append("\n\n");
        builder.append("Xin chào ").append(defaultText(event.getRecipientName(), "bạn")).append(",\n\n");
        builder.append(content.summary()).append("\n\n");
        builder.append("Mã hồ sơ: ").append(shortRequestId(event)).append("\n");
        builder.append("Người xét duyệt: ").append(defaultText(event.getReviewerName(), "Đội ngũ SkillSwap")).append("\n");
        if (event.getSubmittedAt() != null) {
            builder.append("Ngày gửi hồ sơ: ").append(formatDateTime(event.getSubmittedAt())).append("\n");
        }
        if (event.getReviewedAt() != null) {
            builder.append("Ngày xét duyệt: ").append(formatDateTime(event.getReviewedAt())).append("\n");
        }
        if (trimToNull(event.getReviewNote()) != null) {
            builder.append("Ghi chú xét duyệt: ").append(event.getReviewNote()).append("\n");
        }
        builder.append("\nBước tiếp theo: ").append(content.nextStep()).append("\n");
        builder.append("Truy cập SkillSwap: ").append(PLATFORM_URL).append("\n");
        return builder.toString();
    }

    private String detailRow(String label, String value) {
        return HtmlEmailTemplate.detailRow(label, value);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "Chưa xác định" : value.format(DATE_TIME_FORMATTER);
    }

    private String shortRequestId(MentorVerificationEmailNotificationEvent event) {
        if (event.getRequestId() == null) {
            return "N/A";
        }
        String value = event.getRequestId().toString();
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
