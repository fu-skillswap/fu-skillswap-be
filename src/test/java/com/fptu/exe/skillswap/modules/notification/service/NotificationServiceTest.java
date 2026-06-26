package com.fptu.exe.skillswap.modules.notification.service;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.notification.domain.Notification;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.dto.response.NotificationResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationService notificationService;

    private User mockUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockUser = new User();
        mockUser.setId(userId);
    }

    @Test
    void createNotification_shouldPersistUnreadNotification() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.createNotification(userId, NotificationType.BOOKING_ACCEPTED, "Title", "Message", "BOOKING", UUID.randomUUID());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(userId, saved.getRecipientUser().getId());
        assertEquals("Title", saved.getTitle());
        assertNull(saved.getReadAt());
        verify(eventPublisher).publishEvent(any(com.fptu.exe.skillswap.modules.notification.event.NotificationCreatedEvent.class));
    }

    @Test
    void getMyNotifications_shouldReturnOnlyCurrentUserNotifications() {
        Notification notif1 = Notification.builder().id(UUID.randomUUID()).recipientUser(mockUser).type(NotificationType.BOOKING_ACCEPTED).title("t1").build();
        Notification notif2 = Notification.builder().id(UUID.randomUUID()).recipientUser(mockUser).type(NotificationType.BOOKING_REJECTED).title("t2").build();
        when(notificationRepository.findByRecipientUserId(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notif1, notif2)));

        PageResponse<NotificationResponse> res = notificationService.getMyNotifications(userId, false, PageRequest.of(0, 10));

        assertEquals(2, res.getContent().size());
        verify(notificationRepository).findByRecipientUserId(eq(userId), any(Pageable.class));
    }

    @Test
    void getMyNotifications_unreadOnly_shouldReturnOnlyUnread() {
        Notification notif1 = Notification.builder().id(UUID.randomUUID()).recipientUser(mockUser).type(NotificationType.BOOKING_ACCEPTED).title("t1").build();
        when(notificationRepository.findByRecipientUserIdAndReadAtIsNull(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notif1)));

        PageResponse<NotificationResponse> res = notificationService.getMyNotifications(userId, true, PageRequest.of(0, 10));

        assertEquals(1, res.getContent().size());
        verify(notificationRepository).findByRecipientUserIdAndReadAtIsNull(eq(userId), any(Pageable.class));
    }

    @Test
    void getMyUnreadCount_shouldReturnUnreadCount() {
        when(notificationRepository.countByRecipientUserIdAndReadAtIsNull(userId)).thenReturn(5L);
        long count = notificationService.getMyUnreadCount(userId);
        assertEquals(5L, count);
    }

    @Test
    void markAsRead_shouldSetReadAt() {
        UUID notifId = UUID.randomUUID();
        Notification notif = Notification.builder().id(notifId).recipientUser(mockUser).type(NotificationType.BOOKING_ACCEPTED).title("t1").build();
        when(notificationRepository.findByIdAndRecipientUserId(notifId, userId)).thenReturn(Optional.of(notif));

        notificationService.markAsRead(userId, notifId);

        assertNotNull(notif.getReadAt());
        verify(notificationRepository).save(notif);
    }

    @Test
    void markAsRead_shouldBeIdempotentWhenAlreadyRead() {
        UUID notifId = UUID.randomUUID();
        LocalDateTime oldReadAt = LocalDateTime.now().minusDays(1);
        Notification notif = Notification.builder().id(notifId).recipientUser(mockUser).type(NotificationType.BOOKING_ACCEPTED).title("t1").readAt(oldReadAt).build();
        when(notificationRepository.findByIdAndRecipientUserId(notifId, userId)).thenReturn(Optional.of(notif));

        notificationService.markAsRead(userId, notifId);

        assertEquals(oldReadAt, notif.getReadAt());
        verify(notificationRepository, never()).save(notif);
    }

    @Test
    void markAsRead_shouldRejectOtherUsersNotification() {
        UUID notifId = UUID.randomUUID();
        when(notificationRepository.findByIdAndRecipientUserId(notifId, userId)).thenReturn(Optional.empty());

        assertThrows(BaseException.class, () -> notificationService.markAsRead(userId, notifId));
    }

    @Test
    void markAllAsRead_shouldOnlyMarkCurrentUserNotifications() {
        notificationService.markAllAsRead(userId);
        verify(notificationRepository).markAllAsRead(eq(userId), any(LocalDateTime.class));
    }

    @Test
    void getMyNotifications_shouldRespectPageSizeCap() {
        when(notificationRepository.findByRecipientUserId(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // Request 1000 items
        notificationService.getMyNotifications(userId, false, PageRequest.of(0, 1000));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByRecipientUserId(eq(userId), captor.capture());

        Pageable usedPageable = captor.getValue();
        // Capped to 50
        assertEquals(50, usedPageable.getPageSize());
    }
}
