package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.event.NotificationEvent;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingDisputeNotificationServiceTest {

    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private UserQueryPort userQueryPort;

    private BookingDisputeNotificationService service;
    private User mentee;
    private User mentor;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new BookingDisputeNotificationService(eventPublisher, userQueryPort,
                TimeProvider.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC));
        mentee = user("mentee@skillswap.vn", "Mentee");
        mentor = user("mentor@skillswap.vn", "Mentor");
        booking = Booking.builder()
                .id(UUID.randomUUID())
                .mentee(mentee)
                .mentorProfile(MentorProfile.builder().userId(mentor.getId()).user(mentor).build())
                .serviceTitleSnapshot("Mock interview")
                .issueSubmittedByUserId(mentee.getId())
                .completionOutcome(BookingCompletionOutcome.NO_SHOW_MENTOR)
                .build();
    }

    @Test
    void issueReported_notifiesAndEmailsOnlyCounterparty() {
        service.notifyIssueReported(booking, mentee.getId());

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        NotificationEvent notification = events.getAllValues().stream()
                .filter(NotificationEvent.class::isInstance).map(NotificationEvent.class::cast).findFirst().orElseThrow();
        BookingEmailNotificationEvent email = events.getAllValues().stream()
                .filter(BookingEmailNotificationEvent.class::isInstance).map(BookingEmailNotificationEvent.class::cast).findFirst().orElseThrow();

        assertEquals(mentor.getId(), notification.recipientUserId());
        assertEquals(NotificationType.BOOKING_ISSUE_REPORTED, notification.type());
        assertEquals(mentor.getEmail(), email.getRecipientEmail());
        assertEquals(BookingEmailNotificationEvent.EventType.BOOKING_ISSUE_REPORTED_EMAIL, email.getEventType());
    }

    @Test
    void issueResponse_notifiesAndEmailsReporter() {
        service.notifyIssueResponded(booking, mentor.getId());

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        NotificationEvent notification = events.getAllValues().stream()
                .filter(NotificationEvent.class::isInstance).map(NotificationEvent.class::cast).findFirst().orElseThrow();
        assertEquals(mentee.getId(), notification.recipientUserId());
        assertEquals(NotificationType.BOOKING_ISSUE_RESPONSE_RECEIVED, notification.type());
    }

    @Test
    void resolvedIssue_notifiesAndEmailsBothParticipants() {
        service.notifyIssueResolved(booking, true);

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(4)).publishEvent(events.capture());
        long notificationCount = events.getAllValues().stream()
                .filter(NotificationEvent.class::isInstance)
                .map(NotificationEvent.class::cast)
                .filter(event -> event.type() == NotificationType.BOOKING_ISSUE_RESOLVED)
                .count();
        assertEquals(2, notificationCount);
    }

    @Test
    void humanReview_notifiesAndEmailsActiveAdminsOnly() {
        User activeAdmin = user("admin@skillswap.vn", "Admin");
        User inactiveAdmin = user("inactive@skillswap.vn", "Inactive");
        inactiveAdmin.setStatus(com.fptu.exe.skillswap.modules.identity.domain.UserStatus.INACTIVE);
        when(userQueryPort.findUsersByRole(eq(RoleCode.ADMIN), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(activeAdmin, inactiveAdmin)));
        when(userQueryPort.findUsersByRole(eq(RoleCode.SYSTEM_ADMIN), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(activeAdmin)));

        service.notifyHumanReviewRequired(booking);

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        NotificationEvent notification = events.getAllValues().stream()
                .filter(NotificationEvent.class::isInstance).map(NotificationEvent.class::cast).findFirst().orElseThrow();
        assertEquals(activeAdmin.getId(), notification.recipientUserId());
        assertEquals(NotificationType.BOOKING_ISSUE_ADMIN_REVIEW_REQUIRED, notification.type());
    }

    private User user(String email, String name) {
        return User.builder().id(UUID.randomUUID()).email(email).fullName(name).build();
    }
}
