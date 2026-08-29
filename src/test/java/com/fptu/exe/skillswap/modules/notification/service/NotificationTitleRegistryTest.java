package com.fptu.exe.skillswap.modules.notification.service;

import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.modules.notification.strategy.BookingNotificationTitleResolver;
import com.fptu.exe.skillswap.modules.notification.strategy.ForumNotificationTitleResolver;
import com.fptu.exe.skillswap.modules.notification.strategy.MentorNotificationTitleResolver;
import com.fptu.exe.skillswap.modules.notification.strategy.NotificationTitleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationTitleRegistryTest {

    private NotificationTitleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NotificationTitleRegistry(List.of(
                new BookingNotificationTitleResolver(),
                new MentorNotificationTitleResolver(),
                new ForumNotificationTitleResolver()
        ));
    }

    @Test
    void resolveTitle_bookingTypes() {
        assertEquals("Mentor đã nhận lịch", registry.resolveTitle(NotificationType.BOOKING_ACCEPTED, "Default"));
        assertEquals("Thanh toán đã xác nhận", registry.resolveTitle(NotificationType.BOOKING_PAYMENT_CONFIRMED, "Default"));
        assertEquals("Có yêu cầu mentoring mới", registry.resolveTitle(NotificationType.BOOKING_REQUEST_CREATED, "Default"));
    }

    @Test
    void resolveTitle_mentorTypes() {
        assertEquals("Hồ sơ mentor của bạn đã được duyệt", registry.resolveTitle(NotificationType.MENTOR_VERIFICATION_APPROVED, "Default"));
        assertEquals("Hồ sơ mentor của bạn đã bị từ chối", registry.resolveTitle(NotificationType.MENTOR_VERIFICATION_REJECTED, "Default"));
    }

    @Test
    void resolveTitle_forumTypes() {
        assertEquals("Bài viết có bình luận mới", registry.resolveTitle(NotificationType.FORUM_POST_COMMENTED, "Default"));
        assertEquals("Tin nhắn mới", registry.resolveTitle(NotificationType.CHAT_UNREAD, "Default"));
    }

    @Test
    void resolveTitle_unknownOrNullType_returnsFallback() {
        assertEquals("Fallback Title", registry.resolveTitle(null, "Fallback Title"));
    }
}
