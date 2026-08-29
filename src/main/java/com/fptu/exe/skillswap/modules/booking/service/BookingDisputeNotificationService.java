package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolution;
import com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.modules.notification.NotificationEvent;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every user-facing notification emitted by the booking dispute lifecycle.
 * Events are handled BEFORE_COMMIT and persist notification/email outbox intents in
 * the same transaction as the booking action.
 */
@Service
@RequiredArgsConstructor
public class BookingDisputeNotificationService {

    private final ApplicationEventPublisher eventPublisher;
    private final UserQueryPort userQueryPort;
    private final TimeProvider timeProvider;

    public void notifyIssueReported(Booking booking, UUID reporterUserId) {
        User recipient = counterparty(booking, reporterUserId);
        User reporter = participant(booking, reporterUserId);
        emit(booking, recipient, reporter, NotificationType.BOOKING_ISSUE_REPORTED,
                "Có tranh chấp booking cần phản hồi",
                "Đối tác đã báo vấn đề. Hãy xem minh chứng và phản hồi trong 24 giờ.",
                BookingEmailNotificationEvent.EventType.BOOKING_ISSUE_REPORTED_EMAIL,
                "Loại vấn đề: " + booking.getIssueType());
    }

    public void notifyIssueResponded(Booking booking, UUID responderUserId) {
        User reporter = participant(booking, booking.getIssueSubmittedByUserId());
        User responder = participant(booking, responderUserId);
        emit(booking, reporter, responder, NotificationType.BOOKING_ISSUE_RESPONSE_RECEIVED,
                "Tranh chấp booking đã có phản hồi",
                "Đối tác đã phản hồi tranh chấp. Bạn có thể xem nội dung và minh chứng trong booking.",
                BookingEmailNotificationEvent.EventType.BOOKING_ISSUE_RESPONSE_RECEIVED_EMAIL,
                null);
    }

    public void notifyResponseReminder(Booking booking) {
        User recipient = counterparty(booking, booking.getIssueSubmittedByUserId());
        emit(booking, recipient, participant(booking, booking.getIssueSubmittedByUserId()),
                NotificationType.BOOKING_ISSUE_RESPONSE_REMINDER,
                "Cần phản hồi tranh chấp booking",
                "Bạn còn 12 giờ để phản hồi báo cáo và bổ sung minh chứng nếu cần.",
                BookingEmailNotificationEvent.EventType.BOOKING_ISSUE_RESPONSE_REMINDER_EMAIL,
                null);
    }

    public void notifyHumanReviewRequired(Booking booking) {
        notifyActiveAdmins(booking, NotificationType.BOOKING_ISSUE_ADMIN_REVIEW_REQUIRED,
                "Cần xử lý tranh chấp booking",
                booking.getIssueRespondedAtUtc() == null && booking.getIssueRespondedAt() == null
                        ? "Booking có tranh chấp chưa được counterparty phản hồi sau 24 giờ và cần admin xem xét."
                        : "Hai bên đã gửi thông tin cho tranh chấp booking; case sẵn sàng để admin xem xét.",
                BookingEmailNotificationEvent.EventType.BOOKING_ISSUE_ADMIN_REVIEW_REQUIRED_EMAIL);
    }

    public void notifyAdminSlaBreach(Booking booking) {
        notifyActiveAdmins(booking, NotificationType.ADMIN_DISPUTE_SLA_BREACH,
                "Cảnh báo SLA khiếu nại booking",
                "Booking có khiếu nại chưa được admin xử lý sau 48 giờ.",
                BookingEmailNotificationEvent.EventType.BOOKING_ISSUE_ADMIN_SLA_BREACH_EMAIL);
    }

    public void notifyIssueResolved(Booking booking, boolean autoResolved) {
        notifyIssueResolved(booking, autoResolved, null);
    }

    public void notifyIssueResolved(Booking booking, boolean autoResolved, BookingIssueResolution resolution) {
        String result = resolveMessage(booking, autoResolved, resolution);
        String reason = trimToMax(booking.getIssueResolutionNote(), 500);
        User mentee = mentee(booking);
        User mentor = mentor(booking);
        emit(booking, mentee, null, NotificationType.BOOKING_ISSUE_RESOLVED,
                "Tranh chấp booking đã được xử lý", result,
                BookingEmailNotificationEvent.EventType.BOOKING_ISSUE_RESOLVED_EMAIL, reason);
        if (mentor != null && (mentee == null || !mentor.getId().equals(mentee.getId()))) {
            emit(booking, mentor, null, NotificationType.BOOKING_ISSUE_RESOLVED,
                    "Tranh chấp booking đã được xử lý", result,
                    BookingEmailNotificationEvent.EventType.BOOKING_ISSUE_RESOLVED_EMAIL, reason);
        }
    }

    public void notifyIssueResolutionReversed(Booking booking, BookingIssueResolution reversalRecord) {
        String result = "Quyết định xử lý khiếu nại trước đó đã được quản trị viên đảo ngược và đang được xem xét lại.";
        String reason = reversalRecord != null && reversalRecord.getAdminNote() != null
                ? trimToMax(reversalRecord.getAdminNote(), 500)
                : trimToMax(booking.getIssueResolutionNote(), 500);
        User mentee = mentee(booking);
        User mentor = mentor(booking);
        emit(booking, mentee, null, NotificationType.BOOKING_ISSUE_RESOLUTION_REVERSED,
                "Quyết định khiếu nại booking được xem xét lại", result,
                BookingEmailNotificationEvent.EventType.BOOKING_ISSUE_RESOLUTION_REVERSED_EMAIL, reason);
        if (mentor != null && (mentee == null || !mentor.getId().equals(mentee.getId()))) {
            emit(booking, mentor, null, NotificationType.BOOKING_ISSUE_RESOLUTION_REVERSED,
                    "Quyết định khiếu nại booking được xem xét lại", result,
                    BookingEmailNotificationEvent.EventType.BOOKING_ISSUE_RESOLUTION_REVERSED_EMAIL, reason);
        }
    }

    private void notifyActiveAdmins(Booking booking, NotificationType type, String title, String message,
                                    BookingEmailNotificationEvent.EventType emailType) {
        Map<UUID, User> activeAdmins = new LinkedHashMap<>();
        addActiveAdmins(activeAdmins, RoleCode.ADMIN);
        addActiveAdmins(activeAdmins, RoleCode.SYSTEM_ADMIN);
        activeAdmins.values().forEach(admin -> emit(booking, admin, null, type, title, message, emailType, null));
    }

    private void addActiveAdmins(Map<UUID, User> target, RoleCode roleCode) {
        userQueryPort.findUsersByRole(roleCode, Pageable.unpaged()).getContent().stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .forEach(user -> target.putIfAbsent(user.getId(), user));
    }

    private void emit(Booking booking, User recipient, User actor, NotificationType type, String title,
                      String message, BookingEmailNotificationEvent.EventType emailType, String reason) {
        if (booking == null || recipient == null || recipient.getId() == null) return;
        eventPublisher.publishEvent(new NotificationEvent(recipient.getId(), type, title, message, "BOOKING", booking.getId()));
        if (recipient.getEmail() == null || recipient.getEmail().isBlank()) return;
        eventPublisher.publishEvent(BookingEmailNotificationEvent.builder()
                .bookingId(booking.getId()).eventType(emailType)
                .recipientEmail(recipient.getEmail()).recipientName(recipient.getFullName())
                .actorName(actor == null ? "SkillSwap" : actor.getFullName())
                .bookingStartTime(booking.getSelectedStartTime()).bookingEndTime(booking.getSelectedEndTime())
                .serviceTitle(booking.getServiceTitleSnapshot()).serviceDurationMinutes(booking.getServiceDurationSnapshot())
                .serviceFree(booking.getServiceIsFreeSnapshot()).servicePriceScoin(booking.getServicePriceScoinSnapshot())
                .learningGoalTitle(booking.getLearningGoalTitle()).learningGoalDescription(booking.getLearningGoalDescription())
                .serviceExpectedOutcome(booking.getServiceExpectedOutcomeSnapshot()).reason(reason)
                .createdAt(timeProvider.nowBusiness()).build());
    }

    private User counterparty(Booking booking, UUID actorUserId) {
        User mentee = mentee(booking);
        User mentor = mentor(booking);
        return mentor != null && mentor.getId().equals(actorUserId) ? mentee : mentor;
    }

    private User participant(Booking booking, UUID userId) {
        User mentee = mentee(booking);
        if (mentee != null && mentee.getId().equals(userId)) return mentee;
        User mentor = mentor(booking);
        return mentor != null && mentor.getId().equals(userId) ? mentor : null;
    }

    private User mentee(Booking booking) {
        return booking == null ? null : booking.getMentee();
    }

    private User mentor(Booking booking) {
        return booking == null || booking.getMentorProfile() == null ? null : booking.getMentorProfile().getUser();
    }

    private String resolveMessage(Booking booking, boolean autoResolved, BookingIssueResolution resolution) {
        String prefix = autoResolved ? "Hệ thống đã tự động xử lý tranh chấp. " : "Admin đã xử lý tranh chấp. ";
        if (resolution != null) {
            return prefix + "Kết quả: hoàn " + safeAmount(resolution.getMenteeRefundScoin())
                    + " SCoin cho mentee, thanh toán " + safeAmount(resolution.getMentorSettlementScoin())
                    + " SCoin cho mentor và nền tảng giữ " + safeAmount(resolution.getPlatformSettlementScoin())
                    + " SCoin.";
        }
        BookingCompletionOutcome outcome = booking.getCompletionOutcome();
        if (outcome == BookingCompletionOutcome.NO_SHOW_MENTOR) {
            return prefix + "Mentor được xác nhận không có mặt; booking được hoàn tiền theo chính sách nếu có thanh toán.";
        }
        if (outcome == BookingCompletionOutcome.NO_SHOW_MENTEE) {
            return prefix + "Mentee được xác nhận không có mặt; booking được xử lý theo chính sách thanh toán nếu có.";
        }
        if (outcome == BookingCompletionOutcome.ADMIN_SLA_AUTO_RELEASED) {
            return "Tranh chấp vượt thời hạn xử lý của admin. Theo chính sách công bố, khoản thanh toán được giải ngân cho mentor.";
        }
        return prefix + "Buổi mentoring được xác nhận; thanh toán được xử lý theo chính sách nếu có.";
    }

    private int safeAmount(Integer amount) {
        return amount == null ? 0 : Math.max(0, amount);
    }

    private String trimToMax(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
