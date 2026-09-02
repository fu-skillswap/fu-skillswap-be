package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEvent;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStateTestSupport;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionAttendance;
import com.fptu.exe.skillswap.modules.booking.domain.SessionParticipantRole;
import com.fptu.exe.skillswap.modules.booking.domain.SessionSourceType;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.ConfirmBookingRequest;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.SessionRepository;
import com.fptu.exe.skillswap.modules.booking.repository.SessionAttendanceRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingActivityCommandPort;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.modules.booking.port.BookingSettlementCommandPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSettlementPort;
import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionCompletionUtcTest {

    private TimeZone defaultTimeZone;

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionAttendanceRepository sessionAttendanceRepository;
    @Mock
    private BookingSettlementCommandPort settlementCommandPort;
    @Mock
    private BookingPaymentSettlementPort bookingPaymentSettlementPort;
    @Mock
    private BookingEventService bookingEventService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private InternalTelemetryService internalTelemetryService;
    @Mock
    private MentorBookingActivityCommandPort mentorBookingActivityCommandPort;
    @Mock
    private PaymentOrderService paymentOrderService;
    @Mock
    private UserQueryPort userQueryPort;
    @Mock
    private BookingResponseMapper bookingResponseMapper;

    private SessionService sessionService;
    private SessionFinalizationService sessionFinalizationService;
    private BookingCompletionService bookingCompletionService;
    private BookingLifecycleMaintenanceService maintenanceService;

    private UUID mentorUserId;
    private UUID menteeUserId;
    private UUID bookingId;
    private User mentee;
    private User mentorUser;
    private MentorProfile mentorProfile;
    private Booking booking;
    private Session session;

    @BeforeEach
    void setUp() {
        defaultTimeZone = TimeZone.getDefault();
        mentorUserId = UUID.randomUUID();
        menteeUserId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        mentee = User.builder().id(menteeUserId).email("mentee@example.com").build();
        mentorUser = User.builder().id(mentorUserId).email("mentor@example.com").build();
        mentorProfile = MentorProfile.builder().userId(mentorUserId).totalCompletedSessions(0).totalSessions(0).build();

        Instant startUtc = Instant.parse("2026-09-01T08:00:00Z");
        Instant endUtc = Instant.parse("2026-09-01T09:00:00Z");

        MentorService service = MentorService.builder().id(UUID.randomUUID()).title("Mock Interview").durationMinutes(60).build();
        MentorAvailabilitySlot slot = MentorAvailabilitySlot.builder().id(UUID.randomUUID()).mentorUserId(mentorUserId).startTimeUtc(startUtc).endTimeUtc(endUtc).build();

        booking = Booking.builder()
                .id(bookingId)
                .menteeUserId(mentee.getId())
                .mentorUserId(mentorUserId)
                .serviceId(service.getId())
                .slot(slot)
                .status(BookingStatus.PAID)
                .selectedStartTimeUtc(startUtc)
                .selectedStartTime(BookingTime.fromInstant(startUtc))
                .selectedEndTimeUtc(endUtc)
                .selectedEndTime(BookingTime.fromInstant(endUtc))
                .build();

        session = Session.builder()
                .id(UUID.randomUUID())
                .mentorUserId(mentorUserId)
                .serviceId(service.getId())
                .sourceType(SessionSourceType.BOOKING)
                .sourceId(bookingId)
                .scheduledStartTimeUtc(startUtc)
                .scheduledStartTime(BookingTime.fromInstant(startUtc))
                .scheduledEndTimeUtc(endUtc)
                .scheduledEndTime(BookingTime.fromInstant(endUtc))
                .status(SessionStatus.SCHEDULED)
                .build();

        sessionService = new SessionService(sessionRepository);
        sessionFinalizationService = new SessionFinalizationService(
                sessionRepository, sessionService, mentorBookingActivityCommandPort);
        lenient().doAnswer(invocation -> {
            mentorProfile.setTotalCompletedSessions(mentorProfile.getTotalCompletedSessions() + 1);
            mentorProfile.setTotalSessions(mentorProfile.getTotalSessions() + 1);
            return null;
        }).when(mentorBookingActivityCommandPort).recordCompletedSession(any(UUID.class), any(Instant.class));
        lenient().doAnswer(invocation -> {
            mentorProfile.setLastActiveAt(BookingTime.fromInstant(invocation.getArgument(1, Instant.class)));
            return null;
        }).when(mentorBookingActivityCommandPort).recordMentorActivity(any(UUID.class), any(Instant.class));
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(defaultTimeZone);
    }

    @Test
    @DisplayName("Session entity dual-write synchronizes shadow Instant fields")
    void testSessionDualWriteSync() {
        Session s = new Session();
        s.setScheduledStartTimeUtc(Instant.parse("2026-09-01T08:00:00Z"));
        s.setScheduledEndTimeUtc(Instant.parse("2026-09-01T09:00:00Z"));
        s.onCreate();

        assertThat(s.getScheduledStartTime()).isNotNull();
        assertThat(s.getScheduledStartTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 15, 0, 0));
        assertThat(s.getScheduledEndTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 16, 0, 0));
        assertThat(s.getCreatedAtUtc()).isNotNull();
        assertThat(s.getUpdatedAtUtc()).isNotNull();
    }

    @Test
    @DisplayName("BookingRescheduleRequest entity dual-write synchronizes shadow Instant fields")
    void testBookingRescheduleRequestDualWriteSync() {
        BookingRescheduleRequest req = new BookingRescheduleRequest();
        req.setPreviousSelectedStartTimeUtc(Instant.parse("2026-09-01T08:00:00Z"));
        req.setPreviousSelectedEndTimeUtc(Instant.parse("2026-09-01T09:00:00Z"));
        req.setProposedSelectedStartTimeUtc(Instant.parse("2026-09-02T08:00:00Z"));
        req.setProposedSelectedEndTimeUtc(Instant.parse("2026-09-02T09:00:00Z"));
        req.onCreate();

        assertThat(req.getPreviousSelectedStartTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 15, 0, 0));
        assertThat(req.getProposedSelectedStartTime()).isEqualTo(LocalDateTime.of(2026, 9, 2, 15, 0, 0));
        assertThat(req.getCreatedAtUtc()).isNotNull();
    }

    @Test
    @DisplayName("BookingEvent entity dual-write synchronizes createdAtUtc")
    void testBookingEventDualWriteSync() {
        BookingEvent event = new BookingEvent();
        event.onCreate();

        assertThat(event.getCreatedAtUtc()).isNotNull();
        assertThat(event.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("BookingDeadlinePolicy calculates 24h, 23h, 12h, 48h SLA consistently across timezones")
    void testBookingDeadlinePolicyMultiTimezone() {
        String[] testZones = {"UTC", "Asia/Ho_Chi_Minh", "America/New_York", "Europe/London", "Asia/Tokyo"};
        Instant endUtc = Instant.parse("2026-09-01T09:00:00Z");
        Instant issueUtc = Instant.parse("2026-09-01T10:00:00Z");

        for (String zone : testZones) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));

            Instant reviewDeadline = BookingDeadlinePolicy.resolveReviewDeadlineUtc(endUtc);
            assertThat(reviewDeadline).isEqualTo(Instant.parse("2026-09-02T09:00:00Z"));

            Instant warningDeadline = BookingDeadlinePolicy.resolveAutoCloseWarningDeadlineUtc(endUtc);
            assertThat(warningDeadline).isEqualTo(Instant.parse("2026-09-02T08:00:00Z"));

            Instant issueResponseDeadline = BookingDeadlinePolicy.resolveIssueResponseDeadlineUtc(issueUtc);
            assertThat(issueResponseDeadline).isEqualTo(Instant.parse("2026-09-02T10:00:00Z"));

            Instant escalationDeadline = BookingDeadlinePolicy.resolveIssueEscalationDeadlineUtc(issueUtc);
            assertThat(escalationDeadline).isEqualTo(Instant.parse("2026-09-01T22:00:00Z"));

            Instant adminSlaDeadline = BookingDeadlinePolicy.resolveAdminDisputeSlaDeadlineUtc(issueUtc);
            assertThat(adminSlaDeadline).isEqualTo(Instant.parse("2026-09-03T10:00:00Z"));
        }
    }

    @Test
    @DisplayName("SessionFinalizationService is idempotent and does not double-increment mentor counters")
    void testSessionFinalizationIdempotency() {
        Instant finalizeTime = Instant.parse("2026-09-01T09:15:00Z");
        when(sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, bookingId))
                .thenReturn(Optional.of(session));

        // 1st finalization
        sessionFinalizationService.finalizeDeliveredSession(booking, finalizeTime);

        assertThat(booking.getFinalizedAtUtc()).isEqualTo(finalizeTime);
        assertThat(booking.getCompletedAtUtc()).isEqualTo(finalizeTime);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(mentorProfile.getTotalCompletedSessions()).isEqualTo(1);
        assertThat(mentorProfile.getTotalSessions()).isEqualTo(1);

        // 2nd finalization (simulating repeated scheduler or retry)
        sessionFinalizationService.finalizeDeliveredSession(booking, finalizeTime.plus(Duration.ofMinutes(5)));

        // Mentor counters must remain exactly 1
        assertThat(mentorProfile.getTotalCompletedSessions()).isEqualTo(1);
        assertThat(mentorProfile.getTotalSessions()).isEqualTo(1);
    }

    @Test
    @DisplayName("Mentor reports completion, mentee confirms -> settlement released")
    void testMentorReportAndMenteeConfirmFlow() {
        Instant nowUtc = Instant.parse("2026-09-01T09:05:00Z"); // 5 mins after session end
        TimeProvider fixedTime = TimeProvider.from(Clock.fixed(nowUtc, ZoneId.of("UTC")));

        bookingCompletionService = new BookingCompletionService(
                bookingRepository, sessionFinalizationService, settlementCommandPort, bookingEventService,
                eventPublisher, internalTelemetryService, bookingResponseMapper, fixedTime
        );

        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, bookingId)).thenReturn(Optional.of(session));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 1. Mentor completes
        bookingCompletionService.completeBookingByMentor(mentorUserId, bookingId, new CompleteBookingRequest("Session completed well"));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.AWAITING_MENTEE_CONFIRMATION);
        assertThat(booking.getMentorNote()).isEqualTo("Session completed well");

        // 2. Mentee confirms
        bookingCompletionService.confirmBookingByParticipant(menteeUserId, bookingId, new ConfirmBookingRequest("Great session!"));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getCompletionOutcome()).isEqualTo(BookingCompletionOutcome.USER_CONFIRMED);
        assertThat(booking.getMenteeNote()).isEqualTo("Great session!");

        verify(settlementCommandPort, times(1)).requestBookingRelease(booking.getId());
    }

    @Test
    @DisplayName("Auto-close at sessionEnd + 24h triggers release and overdue violation if mentor didn't complete")
    void testAutoCloseAt24h() {
        Instant nowUtc = Instant.parse("2026-09-02T09:01:00Z"); // sessionEnd (09:00 Sep 1) + 24h 1min
        TimeProvider fixedTime = TimeProvider.from(Clock.fixed(nowUtc, ZoneId.of("UTC")));

        maintenanceService = new BookingLifecycleMaintenanceService(
                bookingRepository, paymentOrderService, settlementCommandPort, bookingPaymentSettlementPort, eventPublisher, bookingEventService, userQueryPort
        );
        maintenanceService.setTimeProvider(fixedTime);
        maintenanceService.setSessionFinalizationService(sessionFinalizationService);

        BookingStateTestSupport.setStatus(booking, BookingStatus.AWAITING_MENTOR_COMPLETION);
        when(bookingRepository.findTop100ByStatusAndSelectedEndTimeUtcBeforeOrderBySelectedEndTimeUtcAsc(eq(BookingStatus.PAID), any()))
                .thenReturn(List.of());
        when(bookingRepository.findTop100ByStatusAndSelectedEndTimeUtcBeforeOrderBySelectedEndTimeUtcAsc(eq(BookingStatus.AWAITING_MENTOR_COMPLETION), any()))
                .thenReturn(List.of(booking));
        when(bookingRepository.findTop100ByStatusAndSelectedEndTimeUtcBeforeOrderBySelectedEndTimeUtcAsc(eq(BookingStatus.AWAITING_MENTEE_CONFIRMATION), any()))
                .thenReturn(List.of());
        when(bookingRepository.findTop100ByStatusAndIssueSubmittedAtUtcBeforeOrderByIssueSubmittedAtUtcAsc(eq(BookingStatus.UNDER_REVIEW), any()))
                .thenReturn(List.of());
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, bookingId)).thenReturn(Optional.of(session));
        int changed = maintenanceService.processPostSessionLifecycle();
        assertThat(changed).isEqualTo(1);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getCompletionOutcome()).isEqualTo(BookingCompletionOutcome.AUTO_CLOSED);
        assertThat(booking.getMentorCompletionOverdueAtUtc()).isNotNull();

        verify(settlementCommandPort, times(1)).requestBookingRelease(booking.getId());
    }

    @Test
    @DisplayName("Dispute submitted, counterparty does not respond within 24h -> auto-resolved refund")
    void testDisputeAutoResolveAfter24h() {
        Instant submitTime = Instant.parse("2026-09-01T10:00:00Z");
        Instant nowUtc = Instant.parse("2026-09-02T10:05:00Z"); // submitTime + 24h 5m
        TimeProvider fixedTime = TimeProvider.from(Clock.fixed(nowUtc, ZoneId.of("UTC")));

        maintenanceService = new BookingLifecycleMaintenanceService(
                bookingRepository, paymentOrderService, settlementCommandPort, bookingPaymentSettlementPort, eventPublisher, bookingEventService, userQueryPort
        );
        maintenanceService.setTimeProvider(fixedTime);
        maintenanceService.setSessionFinalizationService(sessionFinalizationService);
        maintenanceService.setSessionAttendanceRepository(sessionAttendanceRepository);

        BookingStateTestSupport.setStatus(booking, BookingStatus.UNDER_REVIEW);
        booking.setIssueType(BookingIssueType.MENTOR_NO_SHOW);
        booking.setIssueSubmittedAtUtc(submitTime);
        booking.setIssueSubmittedAt(BookingTime.fromInstant(submitTime));
        booking.setIssueSubmittedByUserId(menteeUserId);

        when(bookingRepository.findTop100ByStatusAndSelectedEndTimeUtcBeforeOrderBySelectedEndTimeUtcAsc(any(), any())).thenReturn(List.of());
        when(bookingRepository.findTop100ByStatusAndIssueSubmittedAtUtcBeforeOrderByIssueSubmittedAtUtcAsc(eq(BookingStatus.UNDER_REVIEW), any()))
                .thenReturn(List.of(booking));
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, bookingId)).thenReturn(Optional.of(session));
        when(sessionAttendanceRepository.findByBookingId(bookingId)).thenReturn(List.of(SessionAttendance.builder()
                .session(session)
                .participantRole(SessionParticipantRole.MENTEE)
                .participantUserId(menteeUserId)
                .checkedInAtUtc(submitTime.minus(Duration.ofHours(1)))
                .build()));
        when(bookingPaymentSettlementPort.findCancellationContext(bookingId)).thenReturn(Optional.of(
                new com.fptu.exe.skillswap.modules.booking.port.BookingCancellationContext(
                        bookingId, menteeUserId, mentorUserId, booking.getStatus().name(), null,
                        null, null, null, false, true)));

        int changed = maintenanceService.processPostSessionLifecycle();
        assertThat(changed).isEqualTo(1);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getCompletionOutcome()).isEqualTo(BookingCompletionOutcome.NO_SHOW_MENTOR);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);

        verify(settlementCommandPort, times(1)).requestMentorNoShowRefund(booking.getId());
    }

    @Test
    @DisplayName("No-show without one-sided attendance evidence remains under admin review")
    void testDisputeWithoutAttendanceRequiresAdminReview() {
        Instant submitTime = Instant.parse("2026-09-01T10:00:00Z");
        Instant nowUtc = Instant.parse("2026-09-02T10:05:00Z");
        TimeProvider fixedTime = TimeProvider.from(Clock.fixed(nowUtc, ZoneId.of("UTC")));

        maintenanceService = new BookingLifecycleMaintenanceService(
                bookingRepository, paymentOrderService, settlementCommandPort, bookingPaymentSettlementPort, eventPublisher, bookingEventService, userQueryPort
        );
        maintenanceService.setTimeProvider(fixedTime);
        maintenanceService.setSessionFinalizationService(sessionFinalizationService);
        maintenanceService.setSessionAttendanceRepository(sessionAttendanceRepository);

        BookingStateTestSupport.setStatus(booking, BookingStatus.UNDER_REVIEW);
        booking.setIssueType(BookingIssueType.MENTOR_NO_SHOW);
        booking.setIssueSubmittedAtUtc(submitTime);
        booking.setIssueSubmittedAt(BookingTime.fromInstant(submitTime));
        booking.setIssueSubmittedByUserId(menteeUserId);

        when(bookingRepository.findTop100ByStatusAndSelectedEndTimeUtcBeforeOrderBySelectedEndTimeUtcAsc(any(), any())).thenReturn(List.of());
        when(bookingRepository.findTop100ByStatusAndIssueSubmittedAtUtcBeforeOrderByIssueSubmittedAtUtcAsc(eq(BookingStatus.UNDER_REVIEW), any()))
                .thenReturn(List.of(booking));
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(sessionAttendanceRepository.findByBookingId(bookingId)).thenReturn(List.of());

        int changed = maintenanceService.processPostSessionLifecycle();

        assertThat(changed).isEqualTo(1);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.UNDER_REVIEW);
        assertThat(booking.getIssueResolutionNote()).isEqualTo("SYSTEM_ATTENDANCE_REQUIRES_ADMIN_REVIEW");
        verify(settlementCommandPort, never()).requestMentorNoShowRefund(booking.getId());
        verify(settlementCommandPort, never()).requestBookingRelease(booking.getId());
    }
}
