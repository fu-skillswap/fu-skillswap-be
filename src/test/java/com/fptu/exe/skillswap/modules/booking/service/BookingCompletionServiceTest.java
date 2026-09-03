package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction;
import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionReasonCode;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDisputeSlaStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionExecutor;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.ConfirmBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RespondBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SubmitBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.AdminResolveBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.AdminReverseResolutionRequest;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingIssueResolutionRepository;
import com.fptu.exe.skillswap.modules.booking.port.BookingSettlementCommandPort;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCompletionServiceTest {

    private static final ZoneId APP_ZONE = TimeProvider.BUSINESS_ZONE;
    private static final Instant FIXED_NOW = Instant.parse("2026-08-23T03:00:00Z");

    @Mock private BookingRepository bookingRepository;
    @Mock private SessionFinalizationService sessionFinalizationService;
    @Mock private BookingSettlementCommandPort settlementCommandPort;
    @Mock private BookingEventService bookingEventService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private InternalTelemetryService internalTelemetryService;
    @Mock private BookingResponseMapper bookingResponseMapper;
    @Mock private BookingIssueEvidenceService bookingIssueEvidenceService;
    @Mock private BookingIssueResolutionRepository bookingIssueResolutionRepository;

    private TimeProvider timeProvider;
    private BookingCompletionService service;
    private UUID bookingId;
    private UUID menteeId;
    private UUID mentorId;

    @BeforeEach
    void setUp() {
        timeProvider = TimeProvider.fixed(FIXED_NOW, APP_ZONE);
        service = new BookingCompletionService(
                bookingRepository,
                sessionFinalizationService,
                settlementCommandPort,
                bookingEventService,
                eventPublisher,
                internalTelemetryService,
                bookingResponseMapper,
                timeProvider
        );
        service.setBookingIssueEvidenceService(bookingIssueEvidenceService);
        service.setBookingIssueResolutionRepository(bookingIssueResolutionRepository);
        bookingId = UUID.randomUUID();
        menteeId = UUID.randomUUID();
        mentorId = UUID.randomUUID();
    }

    @Test
    void submitIssue_menteeReportsMenteeNoShow_shouldRejectBeforeMutation() {
        Booking booking = eligibleBooking();
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () -> service.submitBookingIssue(
                menteeId,
                bookingId,
                new SubmitBookingIssueRequest(BookingIssueType.MENTEE_NO_SHOW, "Mentee không tham gia", List.of(UUID.randomUUID()))
        ));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals(BookingStatus.AWAITING_MENTOR_COMPLETION, booking.getStatus());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void submitIssue_mentorReportsMentorNoShow_shouldRejectBeforeMutation() {
        Booking booking = eligibleBooking();
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () -> service.submitBookingIssue(
                mentorId,
                bookingId,
                new SubmitBookingIssueRequest(BookingIssueType.MENTOR_NO_SHOW, "Mentor không tham gia", List.of(UUID.randomUUID()))
        ));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals(BookingStatus.AWAITING_MENTOR_COMPLETION, booking.getStatus());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void submitIssue_menteeReportsMentorNoShow_shouldAccept() {
        Booking booking = eligibleBooking();
        stubSave(booking);

        var response = service.submitBookingIssue(
                menteeId,
                bookingId,
                new SubmitBookingIssueRequest(BookingIssueType.MENTOR_NO_SHOW, "Mentor không tham gia", List.of(UUID.randomUUID()))
        );

        assertEquals(BookingStatus.UNDER_REVIEW, response.status());
        assertEquals(BookingIssueType.MENTOR_NO_SHOW, response.issueType());
    }

    @Test
    void submitIssue_participantReportsGenericIssue_shouldAccept() {
        Booking booking = eligibleBooking();
        stubSave(booking);

        var response = service.submitBookingIssue(
                mentorId,
                bookingId,
                new SubmitBookingIssueRequest(BookingIssueType.TECHNICAL_PROBLEM, "Không thể kết nối cuộc gọi", List.of(UUID.randomUUID()))
        );

        assertEquals(BookingStatus.UNDER_REVIEW, response.status());
        assertEquals(BookingIssueType.TECHNICAL_PROBLEM, response.issueType());
        assertEquals(FIXED_NOW.plusSeconds(24 * 60 * 60), response.issueResponseDeadlineAt().toInstant());
        assertEquals(BookingDisputeSlaStatus.WAITING_COUNTERPARTY, response.disputeSlaStatus());
        assertNull(response.issueAdminEscalatedAt());
    }

    @Test
    void respondToIssue_shouldReturnAdminSlaMetadataImmediately() {
        Booking booking = eligibleBooking();
        Instant submittedUtc = FIXED_NOW.minusSeconds(60 * 60);
        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.ISSUE_REPORTED, submittedUtc);
        booking.setIssueSubmittedAtUtc(submittedUtc);
        booking.setIssueSubmittedAt(LocalDateTime.ofInstant(submittedUtc, APP_ZONE));
        booking.setIssueSubmittedByUserId(menteeId);
        booking.setIssueType(BookingIssueType.TECHNICAL_PROBLEM);
        stubSave(booking);

        var response = service.respondToBookingIssue(
                mentorId,
                bookingId,
                new RespondBookingIssueRequest("Đã vào phòng họp nhưng mất kết nối", List.of())
        );

        assertEquals(FIXED_NOW, response.issueAdminEscalatedAt().toInstant());
        assertEquals(FIXED_NOW.plusSeconds(48 * 60 * 60), response.issueAdminResolutionDeadlineAt().toInstant());
        assertEquals(BookingDisputeSlaStatus.WAITING_ADMIN, response.disputeSlaStatus());
        assertNull(response.issueAdminSlaOverdueAt());
    }

    @Test
    void respondToIssue_atResponseDeadline_shouldReject() {
        Booking booking = eligibleBooking();
        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.ISSUE_REPORTED, FIXED_NOW);
        booking.setIssueSubmittedAtUtc(FIXED_NOW.minusSeconds(24 * 60 * 60));
        booking.setIssueSubmittedAt(LocalDateTime.ofInstant(FIXED_NOW.minusSeconds(24 * 60 * 60), APP_ZONE));
        booking.setIssueSubmittedByUserId(menteeId);
        booking.setIssueType(BookingIssueType.TECHNICAL_PROBLEM);
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () -> service.respondToBookingIssue(
                mentorId,
                bookingId,
                new RespondBookingIssueRequest("Phản hồi quá hạn", List.of())
        ));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void completeBookingByMentor_shouldDelegateSessionRecordingToFinalizationService() {
        Booking booking = eligibleBooking();
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        service.completeBookingByMentor(mentorId, bookingId, new CompleteBookingRequest("Đã hoàn tất"));

        assertEquals(BookingStatus.AWAITING_MENTEE_CONFIRMATION, booking.getStatus());
        assertNull(booking.getCompletedAt());
        verify(sessionFinalizationService).recordMentorReportedCompletion(
                org.mockito.ArgumentMatchers.eq(booking), any(Instant.class));
    }

    @Test
    void completeBookingByMentor_duplicateAfterMentorCompletion_shouldRejectWithoutSideEffects() {
        Booking booking = eligibleBooking(BookingStatus.AWAITING_MENTEE_CONFIRMATION);
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));

        BaseException exception = assertThrows(BaseException.class, () -> service.completeBookingByMentor(
                mentorId, bookingId, new CompleteBookingRequest("Retry completion")));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
        verify(sessionFinalizationService, never()).recordMentorReportedCompletion(any(Booking.class), any(Instant.class));
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void confirmBookingByParticipant_shouldDelegateFinalizationBeforeSettlementRelease() {
        Booking booking = eligibleBooking();
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        service.confirmBookingByParticipant(menteeId, bookingId, new ConfirmBookingRequest("Đã học xong"));

        verify(sessionFinalizationService).finalizeDeliveredSession(
                org.mockito.ArgumentMatchers.eq(booking), any(Instant.class));
        verify(settlementCommandPort).requestBookingRelease(booking.getId());
    }

    @Test
    void confirmBookingByParticipant_duplicateAfterUserConfirmation_shouldBeIdempotent() {
        Booking booking = eligibleBooking(BookingStatus.COMPLETED);
        booking.setCompletionOutcome(com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome.USER_CONFIRMED);
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));

        assertNull(service.confirmBookingByParticipant(
                menteeId, bookingId, new ConfirmBookingRequest("Retry confirmation")));

        verify(sessionFinalizationService, never()).finalizeDeliveredSession(any(Booking.class), any(Instant.class));
        verify(settlementCommandPort, never()).requestBookingRelease(any());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void resolveBookingIssue_whenDecisionAlreadyApplied_shouldRejectWithoutFinancialSideEffects() {
        Booking booking = eligibleBooking(BookingStatus.UNDER_REVIEW);
        booking.setIssueType(BookingIssueType.QUALITY_ISSUE);
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingIssueResolutionRepository.findFirstByBookingIdAndResolutionKindAndStatusOrderByCreatedAtUtcDesc(
                any(), any(), any())).thenReturn(Optional.of(new com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolution()));

        BaseException exception = assertThrows(BaseException.class, () -> service.resolveBookingIssue(
                UUID.randomUUID(), bookingId, new AdminResolveBookingIssueRequest(
                        AdminBookingIssueResolutionAction.CONFIRM_SESSION,
                        AdminBookingIssueResolutionReasonCode.SESSION_CONFIRMED,
                        "Duplicate resolution attempt", null, null, null)));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
        verify(settlementCommandPort, never()).requestAdminIssueResolution(any(), any());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void resolvePartialSettlement_shouldFinalizeSessionWithoutFullCompletionCounterAndDelegateOneSettlement() {
        Booking booking = eligibleBooking(BookingStatus.UNDER_REVIEW);
        booking.setIssueType(BookingIssueType.QUALITY_ISSUE);
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(bookingIssueResolutionRepository.findFirstByBookingIdAndResolutionKindAndStatusOrderByCreatedAtUtcDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(bookingIssueResolutionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            var resolution = invocation.getArgument(0, com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolution.class);
            resolution.setId(UUID.randomUUID());
            return resolution;
        });

        service.resolveBookingIssue(mentorId, bookingId, new AdminResolveBookingIssueRequest(
                AdminBookingIssueResolutionAction.PARTIAL_SETTLEMENT,
                AdminBookingIssueResolutionReasonCode.QUALITY_PARTIAL_COMPENSATION,
                "Nội dung chỉ đáp ứng một phần mục tiêu đã đặt ra.",
                5000, 3500, 1500
        ));

        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        assertEquals(com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome.PARTIALLY_SETTLED,
                booking.getCompletionOutcome());
        verify(sessionFinalizationService).finalizeDisputedSessionWithoutCompletionCounter(
                org.mockito.ArgumentMatchers.eq(booking), any(Instant.class));
        verify(settlementCommandPort).requestAdminIssueResolution(
                org.mockito.ArgumentMatchers.eq(booking.getId()), org.mockito.ArgumentMatchers.any(UUID.class));
    }

    @Test
    void reverseBookingIssueResolution_whenCompletedWithAppliedResolution_shouldReopenDispute() {
        Booking booking = eligibleBooking(BookingStatus.COMPLETED);
        booking.setCompletionOutcome(com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome.PARTIALLY_SETTLED);

        var originalResolution = com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolution.builder()
                .id(UUID.randomUUID())
                .bookingId(bookingId)
                .resolutionKind(com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionKind.RESOLUTION)
                .status(com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionStatus.APPLIED)
                .action(AdminBookingIssueResolutionAction.PARTIAL_SETTLEMENT)
                .reasonCode(AdminBookingIssueResolutionReasonCode.QUALITY_PARTIAL_COMPENSATION)
                .menteeBps(5000)
                .mentorBps(3500)
                .platformBps(1500)
                .menteeRefundScoin(50)
                .mentorSettlementScoin(35)
                .platformSettlementScoin(15)
                .escrowScoin(100)
                .build();

        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(bookingIssueResolutionRepository.findFirstByBookingIdAndResolutionKindAndStatusOrderByCreatedAtUtcDesc(
                any(), any(), any())).thenReturn(Optional.of(originalResolution));
        when(bookingIssueResolutionRepository.existsByReversalOfResolutionIdAndResolutionKind(any(), any()))
                .thenReturn(false);
        when(bookingIssueResolutionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            var resolution = invocation.getArgument(0, com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolution.class);
            resolution.setId(UUID.randomUUID());
            return resolution;
        });

        var response = service.reverseBookingIssueResolution(
                UUID.randomUUID(),
                bookingId,
                new AdminReverseResolutionRequest(
                        AdminBookingIssueResolutionReasonCode.OTHER,
                        "Phát hiện sai sót trong biên bản đối soát, cần xem xét lại"
                )
        );

        assertEquals(BookingStatus.UNDER_REVIEW, booking.getStatus());
        assertEquals(com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome.UNDER_REVIEW, booking.getCompletionOutcome());
        assertEquals(com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionStatus.REVERSED, originalResolution.getStatus());
        verify(settlementCommandPort).requestResolutionReversal(
                org.mockito.ArgumentMatchers.eq(booking.getId()), org.mockito.ArgumentMatchers.eq(originalResolution.getId()),
                org.mockito.ArgumentMatchers.any(UUID.class));
    }

    @Test
    void reverseBookingIssueResolution_whenAlreadyReversed_shouldRejectDuplicate() {
        Booking booking = eligibleBooking(BookingStatus.COMPLETED);

        var originalResolution = com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolution.builder()
                .id(UUID.randomUUID())
                .bookingId(bookingId)
                .resolutionKind(com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionKind.RESOLUTION)
                .status(com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionStatus.APPLIED)
                .build();

        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingIssueResolutionRepository.findFirstByBookingIdAndResolutionKindAndStatusOrderByCreatedAtUtcDesc(
                any(), any(), any())).thenReturn(Optional.of(originalResolution));
        when(bookingIssueResolutionRepository.existsByReversalOfResolutionIdAndResolutionKind(any(), any()))
                .thenReturn(true);

        BaseException exception = assertThrows(BaseException.class, () -> service.reverseBookingIssueResolution(
                UUID.randomUUID(),
                bookingId,
                new AdminReverseResolutionRequest(
                        AdminBookingIssueResolutionReasonCode.OTHER,
                        "Duplicate reversal attempt"
                )
        ));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    private void stubSave(Booking booking) {
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);
    }

    private Booking eligibleBooking() {
        return eligibleBooking(BookingStatus.AWAITING_MENTOR_COMPLETION);
    }

    private Booking eligibleBooking(BookingStatus status) {
        User mentee = new User();
        mentee.setId(menteeId);
        User mentorUser = new User();
        mentorUser.setId(mentorId);
        MentorProfile mentor = MentorProfile.builder()
                .userId(mentorId)
                .userId(mentorUser.getId())
                .build();
        LocalDateTime now = LocalDateTime.ofInstant(FIXED_NOW, APP_ZONE);
        return Booking.builder()
                .id(bookingId)
                .menteeUserId(mentee.getId())
                .mentorUserId(mentor.getUserId())
                .status(status)
                .selectedStartTime(now.minusHours(2))
                .selectedEndTime(now.minusHours(1))
                .build();
    }
}
