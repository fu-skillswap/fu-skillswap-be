package com.fptu.exe.skillswap.modules.notification.strategy;

import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
@Order(10)
public class BookingNotificationTitleResolver implements NotificationTitleResolver {

    private static final Set<NotificationType> SUPPORTED_TYPES = EnumSet.of(
            NotificationType.BOOKING_REQUEST_CREATED,
            NotificationType.BOOKING_ACCEPTED,
            NotificationType.BOOKING_PAYMENT_CONFIRMED,
            NotificationType.BOOKING_PAYMENT_EXPIRED,
            NotificationType.BOOKING_REJECTED,
            NotificationType.BOOKING_CANCELLED_BY_MENTEE,
            NotificationType.BOOKING_CANCELLED_BY_MENTOR,
            NotificationType.BOOKING_AUTO_REJECTED,
            NotificationType.BOOKING_REQUEST_EXPIRED,
            NotificationType.BOOKING_RESCHEDULE_REQUESTED,
            NotificationType.BOOKING_RESCHEDULE_ACCEPTED,
            NotificationType.BOOKING_RESCHEDULE_REJECTED,
            NotificationType.BOOKING_RESCHEDULE_EXPIRED,
            NotificationType.MEETING_LINK_UPDATED,
            NotificationType.GOOGLE_CALENDAR_SYNC_NOTICE,
            NotificationType.SESSION_COMPLETED,
            NotificationType.BOOKING_REMINDER,
            NotificationType.FEEDBACK_PROMPT,
            NotificationType.FEEDBACK_RECEIVED
    );

    @Override
    public boolean supports(NotificationType type) {
        return type != null && SUPPORTED_TYPES.contains(type);
    }

    @Override
    public String resolveTitle(NotificationType type, String fallbackTitle) {
        if (type == null) {
            return fallbackTitle;
        }
        return switch (type) {
            case BOOKING_REQUEST_CREATED -> "Có yêu cầu mentoring mới";
            case BOOKING_ACCEPTED -> "Mentor đã nhận lịch";
            case BOOKING_PAYMENT_CONFIRMED -> "Thanh toán đã xác nhận";
            case BOOKING_PAYMENT_EXPIRED -> "Hết hạn thanh toán";
            case BOOKING_REJECTED -> "Yêu cầu mentoring bị từ chối";
            case BOOKING_CANCELLED_BY_MENTEE -> "Mentee đã hủy lịch";
            case BOOKING_CANCELLED_BY_MENTOR -> "Mentor đã hủy lịch";
            case BOOKING_AUTO_REJECTED -> "Yêu cầu đã tự hủy";
            case BOOKING_REQUEST_EXPIRED -> "Yêu cầu mentoring đã hết hạn";
            case BOOKING_RESCHEDULE_REQUESTED -> "Có yêu cầu đổi lịch";
            case BOOKING_RESCHEDULE_ACCEPTED -> "Đề nghị đổi lịch đã duyệt";
            case BOOKING_RESCHEDULE_REJECTED -> "Đề nghị đổi lịch bị từ chối";
            case BOOKING_RESCHEDULE_EXPIRED -> "Yêu cầu đổi lịch hết hạn";
            case MEETING_LINK_UPDATED -> "Link buổi học đã cập nhật";
            case GOOGLE_CALENDAR_SYNC_NOTICE -> "Google Calendar cần chú ý";
            case SESSION_COMPLETED -> "Phiên mentoring đã hoàn thành";
            case BOOKING_REMINDER -> "Nhắc lịch mentoring";
            case FEEDBACK_PROMPT -> "Đánh giá buổi mentoring";
            case FEEDBACK_RECEIVED -> "Bạn vừa nhận đánh giá mới";
            default -> fallbackTitle;
        };
    }
}
