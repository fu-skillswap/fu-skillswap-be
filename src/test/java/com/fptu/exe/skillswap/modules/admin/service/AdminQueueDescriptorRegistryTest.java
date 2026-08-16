package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseType;
import com.fptu.exe.skillswap.modules.admin.domain.AdminQueueKey;
import com.fptu.exe.skillswap.modules.admin.strategy.AdminQueueDescriptorRegistry;
import com.fptu.exe.skillswap.modules.admin.strategy.BookingQueueDescriptor;
import com.fptu.exe.skillswap.modules.admin.strategy.EmailOutboxQueueDescriptor;
import com.fptu.exe.skillswap.modules.admin.strategy.ForumReportQueueDescriptor;
import com.fptu.exe.skillswap.modules.admin.strategy.MentorVerificationQueueDescriptor;
import com.fptu.exe.skillswap.modules.admin.strategy.PaymentOrdersFailedQueueDescriptor;
import com.fptu.exe.skillswap.modules.admin.strategy.PayoutRequestQueueDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminQueueDescriptorRegistryTest {

    private AdminQueueDescriptorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AdminQueueDescriptorRegistry(List.of(
                new MentorVerificationQueueDescriptor(),
                new BookingQueueDescriptor(),
                new ForumReportQueueDescriptor(),
                new PayoutRequestQueueDescriptor(),
                new PaymentOrdersFailedQueueDescriptor(),
                new EmailOutboxQueueDescriptor()
        ));
    }

    @Test
    void resolveCaseType_allQueues() {
        assertEquals(AdminCaseType.MENTOR_VERIFICATION_REQUEST, registry.resolveCaseType(AdminQueueKey.MENTOR_VERIFICATION_PENDING_REVIEW));
        assertEquals(AdminCaseType.BOOKING, registry.resolveCaseType(AdminQueueKey.BOOKING_UNDER_REVIEW));
        assertEquals(AdminCaseType.BOOKING, registry.resolveCaseType(AdminQueueKey.BOOKINGS_ACCEPTED_AWAITING_PAYMENT));
        assertEquals(AdminCaseType.FORUM_REPORT, registry.resolveCaseType(AdminQueueKey.FORUM_REPORTS_OPEN));
        assertEquals(AdminCaseType.PAYOUT_REQUEST, registry.resolveCaseType(AdminQueueKey.PAYOUT_REQUESTS_REQUESTED));
        assertEquals(AdminCaseType.PAYMENT_ORDER, registry.resolveCaseType(AdminQueueKey.PAYMENT_ORDERS_FAILED));
        assertEquals(AdminCaseType.EMAIL_OUTBOX, registry.resolveCaseType(AdminQueueKey.EMAIL_OUTBOX_FAILED));
    }

    @Test
    void resolveSeverity_correctLevels() {
        assertEquals("high", registry.resolveSeverity(AdminQueueKey.MENTOR_VERIFICATION_PENDING_REVIEW));
        assertEquals("high", registry.resolveSeverity(AdminQueueKey.BOOKING_UNDER_REVIEW));
        assertEquals("low", registry.resolveSeverity(AdminQueueKey.BOOKINGS_ACCEPTED_AWAITING_PAYMENT));
        assertEquals("medium", registry.resolveSeverity(AdminQueueKey.PAYOUT_REQUESTS_REQUESTED));
        assertEquals("medium", registry.resolveSeverity(AdminQueueKey.EMAIL_OUTBOX_FAILED));
    }

    @Test
    void buildDetailPath_formatsCorrectly() {
        assertEquals("/api/admin/mentor-verification/requests/123", registry.buildDetailPath(AdminQueueKey.MENTOR_VERIFICATION_PENDING_REVIEW, "123"));
        assertEquals("/api/admin/bookings/456", registry.buildDetailPath(AdminQueueKey.BOOKING_UNDER_REVIEW, "456"));
        assertEquals("/api/admin/forum/reports/789", registry.buildDetailPath(AdminQueueKey.FORUM_REPORTS_OPEN, "789"));
    }

    @Test
    void availableActions_emailOutboxHasRetry() {
        assertTrue(registry.availableActions(AdminQueueKey.EMAIL_OUTBOX_FAILED).contains("RETRY_EMAIL"));
        assertEquals(List.of("VIEW_DETAIL", "ASSIGN_TO_ME"), registry.availableActions(AdminQueueKey.BOOKING_UNDER_REVIEW));
    }
}
