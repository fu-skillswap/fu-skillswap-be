package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDisputeSlaStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionExecutor;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.ConfirmBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RespondBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SubmitBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.payment.service.SettlementService;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
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
    @Mock private SettlementService settlementService;
    @Mock private BookingEventService bookingEventService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private InternalTelemetryService internalTelemetryService;
    @Mock private BookingResponseMapper bookingResponseMapper;
    @Mock private BookingIssueEvidenceService bookingIssueEvidenceService;

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
                settlementService,
                bookingEventService,
                eventPublisher,
                internalTelemetryService,
                bookingResponseMapper,
                timeProvider
        );
        service.setBookingIssueEvidenceService(bookingIssueEvidenceService);
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
    void confirmBookingByParticipant_shouldDelegateFinalizationBeforeSettlementRelease() {
        Booking booking = eligibleBooking();
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        service.confirmBookingByParticipant(menteeId, bookingId, new ConfirmBookingRequest("Đã học xong"));

        verify(sessionFinalizationService).finalizeDeliveredSession(
                org.mockito.ArgumentMatchers.eq(booking), any(Instant.class));
        verify(settlementService).releaseForBooking(booking);
    }

    private void stubSave(Booking booking) {
        when(bookingRepository.findByIdForSessionUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);
    }

    private Booking eligibleBooking() {
        User mentee = new User();
        mentee.setId(menteeId);
        User mentorUser = new User();
        mentorUser.setId(mentorId);
        MentorProfile mentor = MentorProfile.builder()
                .userId(mentorId)
                .user(mentorUser)
                .build();
        LocalDateTime now = LocalDateTime.ofInstant(FIXED_NOW, APP_ZONE);
        return Booking.builder()
                .id(bookingId)
                .mentee(mentee)
                .mentorProfile(mentor)
                .status(BookingStatus.AWAITING_MENTOR_COMPLETION)
                .selectedStartTime(now.minusHours(2))
                .selectedEndTime(now.minusHours(1))
                .build();
    }
}
