package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.service.BookingEventService;
import com.fptu.exe.skillswap.modules.booking.service.BookingLifecycleMaintenanceService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.event.NotificationEvent;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.modules.payment.service.SettlementService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingLifecycleMaintenanceServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private PaymentOrderService paymentOrderService;

    @Mock
    private SettlementService settlementService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private BookingEventService bookingEventService;

    @Mock
    private UserRepository userRepository;

    private BookingLifecycleMaintenanceService maintenanceService;

    private User mentee;
    private User mentorUser;
    private MentorProfile mentorProfile;
    private MentorAvailabilitySlot slot;

    @BeforeEach
    void setUp() {
        maintenanceService = new BookingLifecycleMaintenanceService(
                bookingRepository,
                mentorProfileRepository,
                paymentOrderService,
                settlementService,
                eventPublisher,
                bookingEventService,
                userRepository
        );

        UUID menteeId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();

        mentee = User.builder()
                .id(menteeId)
                .email("mentee@test.com")
                .fullName("Mentee Test")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(RoleCode.MENTEE))
                .build();

        mentorUser = User.builder()
                .id(mentorId)
                .email("mentor@test.com")
                .fullName("Mentor Test")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(RoleCode.MENTOR))
                .build();

        mentorProfile = MentorProfile.builder()
                .userId(mentorId)
                .user(mentorUser)
                .status(MentorStatus.ACTIVE)
                .build();

        slot = MentorAvailabilitySlot.builder()
                .id(UUID.randomUUID())
                .mentorProfile(mentorProfile)
                .startTime(LocalDateTime.now().minusHours(10))
                .endTime(LocalDateTime.now().minusHours(8))
                .isActive(true)
                .build();
    }

    @Test
    void processPostSessionLifecycle_awaitingMentorCompletion_afterThreeHours_shouldSendSecondReminder() {
        LocalDateTime now = LocalDateTime.now();
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .slot(slot)
                .status(BookingStatus.AWAITING_MENTOR_COMPLETION)
                .selectedStartTime(now.minusHours(4))
                .selectedEndTime(now.minusHours(3).minusMinutes(5)) // ended 3h5m ago
                .mentorCompletionReminder30mAt(now.minusHours(2).minusMinutes(30))
                .mentorCompletionReminder1hAt(null)
                .build();

        when(bookingRepository.findTop100ByStatusAndSelectedEndTimeBeforeOrderBySelectedEndTimeAsc(
                eq(BookingStatus.AWAITING_MENTOR_COMPLETION), any(LocalDateTime.class)))
                .thenReturn(List.of(booking));
        when(bookingRepository.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));

        int changed = maintenanceService.processPostSessionLifecycle();

        assertEquals(1, changed);
        assertNotNull(booking.getMentorCompletionReminder1hAt());
    }

    @Test
    void processPostSessionLifecycle_awaitingMenteeConfirmation_afterFiveHours_shouldSendWarningNotification() {
        LocalDateTime now = LocalDateTime.now();
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .slot(slot)
                .status(BookingStatus.AWAITING_MENTEE_CONFIRMATION)
                .selectedStartTime(now.minusHours(7))
                .selectedEndTime(now.minusHours(6))
                .completedAt(now.minusHours(5).minusMinutes(10)) // completed 5h10m ago
                .autoCloseWarningSentAt(null)
                .build();

        when(bookingRepository.findTop100ByStatusAndCompletedAtBeforeOrderByCompletedAtAsc(
                eq(BookingStatus.AWAITING_MENTEE_CONFIRMATION), any(LocalDateTime.class)))
                .thenReturn(List.of(booking));
        when(bookingRepository.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));

        int changed = maintenanceService.processPostSessionLifecycle();

        assertEquals(1, changed);
        assertNotNull(booking.getAutoCloseWarningSentAt());
        assertEquals(BookingStatus.AWAITING_MENTEE_CONFIRMATION, booking.getStatus());
        verify(settlementService, never()).releaseForBooking(any());
    }

    @Test
    void processPostSessionLifecycle_awaitingMenteeConfirmation_afterSixHours_shouldAutoCloseAndReleaseSettlement() {
        LocalDateTime now = LocalDateTime.now();
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .slot(slot)
                .status(BookingStatus.AWAITING_MENTEE_CONFIRMATION)
                .selectedStartTime(now.minusHours(8))
                .selectedEndTime(now.minusHours(7))
                .completedAt(now.minusHours(6).minusMinutes(5)) // completed 6h5m ago
                .autoCloseWarningSentAt(now.minusHours(1))
                .build();

        when(bookingRepository.findTop100ByStatusAndCompletedAtBeforeOrderByCompletedAtAsc(
                eq(BookingStatus.AWAITING_MENTEE_CONFIRMATION), any(LocalDateTime.class)))
                .thenReturn(List.of(booking));
        when(bookingRepository.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));

        int changed = maintenanceService.processPostSessionLifecycle();

        assertEquals(1, changed);
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        assertEquals(BookingCompletionOutcome.AUTO_CLOSED, booking.getCompletionOutcome());
        assertNotNull(booking.getAutoClosedAt());
        assertNotNull(booking.getFinalizedAt());
        verify(settlementService).releaseForBooking(eq(booking));
    }

    @Test
    void processPostSessionLifecycle_underReview_overdueFortyEightHours_shouldAlertAdmins() {
        LocalDateTime now = LocalDateTime.now();
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .slot(slot)
                .status(BookingStatus.UNDER_REVIEW)
                .issueType(BookingIssueType.QUALITY_ISSUE)
                .issueDescription("Mentor quality was low")
                .issueSubmittedAt(now.minusHours(49)) // submitted 49h ago
                .adminSlaWarningSentAt(null)
                .build();

        User adminUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin@test.com")
                .fullName("System Admin")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(RoleCode.ADMIN))
                .build();

        when(bookingRepository.findTop100ByStatusAndIssueSubmittedAtBeforeAndAdminSlaWarningSentAtIsNullAndIssueResolvedAtIsNullOrderByIssueSubmittedAtAsc(
                eq(BookingStatus.UNDER_REVIEW), any(LocalDateTime.class)))
                .thenReturn(List.of(booking));
        when(bookingRepository.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(userRepository.findUsersByRole(eq(RoleCode.ADMIN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(adminUser)));
        when(userRepository.findUsersByRole(eq(RoleCode.SYSTEM_ADMIN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        int changed = maintenanceService.processPostSessionLifecycle();

        assertEquals(1, changed);
        assertNotNull(booking.getAdminSlaWarningSentAt());

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent capturedEvent = eventCaptor.getValue();
        assertEquals(adminUser.getId(), capturedEvent.recipientUserId());
        assertEquals(NotificationType.ADMIN_DISPUTE_SLA_BREACH, capturedEvent.type());
    }
}
