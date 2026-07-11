package com.fptu.exe.skillswap.modules.notification;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class NotificationFlowIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EntityManager entityManager;

    private User recipient;
    private User otherUser;

    @BeforeEach
    void setUp() {
        recipient = userRepository.save(User.builder()
                .email("notification-recipient@test.com")
                .fullName("Notification Recipient")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTEE))
                .build());
        otherUser = userRepository.save(User.builder()
                .email("notification-other@test.com")
                .fullName("Notification Other")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTEE))
                .build());
    }

    @Test
    void listUnreadReadAndMarkAll_shouldOnlyAffectCurrentUsersNotifications() {
        UUID bookingOne = UUID.randomUUID();
        UUID bookingTwo = UUID.randomUUID();
        UUID otherBooking = UUID.randomUUID();

        notificationService.createNotification(recipient.getId(), NotificationType.BOOKING_ACCEPTED, "Accepted", "Booking accepted", "BOOKING", bookingOne);
        notificationService.createNotification(recipient.getId(), NotificationType.MEETING_LINK_UPDATED, "Meeting link", "Meeting link updated", "BOOKING", bookingTwo);
        notificationService.createNotification(otherUser.getId(), NotificationType.BOOKING_REJECTED, "Rejected", "Booking rejected", "BOOKING", otherBooking);

        var recipientNotifications = notificationService.getMyNotifications(recipient.getId(), false, PageRequest.of(0, 10));
        assertEquals(2, recipientNotifications.getContent().size());
        assertTrue(recipientNotifications.getContent().stream().allMatch(item -> Set.of(bookingOne, bookingTwo).contains(item.getRelatedEntityId())));
        assertEquals(2L, notificationService.getMyUnreadCount(recipient.getId()));
        assertEquals(1L, notificationService.getMyUnreadCount(otherUser.getId()));

        var firstNotification = recipientNotifications.getContent().getFirst();
        notificationService.markAsRead(recipient.getId(), firstNotification.getNotificationId());

        var unreadOnly = notificationService.getMyNotifications(recipient.getId(), true, PageRequest.of(0, 10));
        assertEquals(1, unreadOnly.getContent().size());
        assertFalse(unreadOnly.getContent().stream().anyMatch(item -> item.getNotificationId().equals(firstNotification.getNotificationId())));
        assertEquals(1L, notificationService.getMyUnreadCount(recipient.getId()));

        notificationService.markAllAsRead(recipient.getId());
        entityManager.flush();
        entityManager.clear();

        var recipientAfterReadAll = notificationService.getMyNotifications(recipient.getId(), false, PageRequest.of(0, 10));
        assertTrue(recipientAfterReadAll.getContent().stream().allMatch(item -> item.getReadAt() != null));
        assertEquals(0L, notificationService.getMyUnreadCount(recipient.getId()));
        assertEquals(1L, notificationService.getMyUnreadCount(otherUser.getId()));
    }

    @Test
    void markAsRead_otherUsersNotification_shouldBeRejected() {
        notificationService.createNotification(otherUser.getId(), NotificationType.BOOKING_ACCEPTED, "Accepted", "Booking accepted", "BOOKING", UUID.randomUUID());
        var otherNotification = notificationService.getMyNotifications(otherUser.getId(), false, PageRequest.of(0, 10)).getContent().getFirst();

        BaseException exception = assertThrows(BaseException.class,
                () -> notificationService.markAsRead(recipient.getId(), otherNotification.getNotificationId()));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        assertNotNull(notificationService.getMyNotifications(otherUser.getId(), true, PageRequest.of(0, 10)).getContent().getFirst().getNotificationId());
    }
}
