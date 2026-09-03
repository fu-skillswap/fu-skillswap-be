package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDisplayState;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDisputeSlaStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingNextAction;
import com.fptu.exe.skillswap.modules.booking.domain.BookingPaymentStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentSettlementStatus;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingResponseMapperTest {

    private static final ZoneId APP_ZONE = TimeProvider.BUSINESS_ZONE;
    private static final Instant FIXED_NOW = Instant.parse("2026-08-23T03:00:00Z");

    private TimeProvider timeProvider;
    private BookingResponseMapper mapper;
    private UUID menteeId;
    private UUID mentorId;

    @BeforeEach
    void setUp() {
        timeProvider = TimeProvider.fixed(FIXED_NOW, APP_ZONE);
        mapper = new BookingResponseMapper(new PaymentProperties(), null);
        mapper.setTimeProvider(timeProvider);
        menteeId = UUID.randomUUID();
        mentorId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void paidSessionJustEnded_menteeGetsConfirmAndIssueActionsWithoutWaitingForScheduler() {
        authenticate(menteeId, RoleCode.MENTEE);
        LocalDateTime now = timeProvider.nowBusiness();
        Booking booking = booking(BookingStatus.PAID, now.minusHours(1), now.minusMinutes(5));

        BookingResponse response = mapper.toBookingResponse(booking);

        assertTrue(response.canConfirmByMentee());
        assertTrue(response.canReportIssue());
        assertTrue(response.canComplete()); // legacy compatibility
        assertEquals(BookingDisplayState.WAITING_CONFIRMATION, response.displayState());
        assertEquals(BookingNextAction.CONFIRM_SESSION, response.nextAction());
        assertEquals("+07:00", response.selectedEndTime().getOffset().toString());
    }

    @Test
    void confirmedSessionWithinJoinWindow_exposesJoinCta() {
        authenticate(menteeId, RoleCode.MENTEE);
        LocalDateTime now = timeProvider.nowBusiness();
        Booking booking = booking(BookingStatus.PAID, now.plusMinutes(10), now.plusMinutes(70));
        booking.setMeetingLink("https://meet.google.com/test-room");

        BookingResponse response = mapper.toBookingResponse(booking);

        assertTrue(response.canJoin());
        assertEquals(BookingNextAction.JOIN_SESSION, response.nextAction());
        assertEquals(BookingDisplayState.UPCOMING, response.displayState());
    }

    @Test
    void pendingRequest_mentorGetsExplicitDecisionCapabilities() {
        authenticate(mentorId, RoleCode.MENTOR);
        LocalDateTime now = timeProvider.nowBusiness();
        Booking booking = booking(BookingStatus.PENDING, now.plusDays(1), now.plusDays(1).plusHours(1));
        booking.setPendingExpireAt(now.plusHours(1));

        BookingResponse response = mapper.toBookingResponse(booking);

        assertTrue(response.canAccept());
        assertTrue(response.canReject());
        assertFalse(response.canPay());
        assertEquals(BookingNextAction.ACCEPT_OR_REJECT, response.nextAction());
    }

    @Test
    void acceptedPaidRequest_menteeGetsPaymentCapabilityAndFourHourDeadline() {
        authenticate(menteeId, RoleCode.MENTEE);
        LocalDateTime now = timeProvider.nowBusiness();
        Booking booking = booking(BookingStatus.ACCEPTED_AWAITING_PAYMENT, now.plusDays(1), now.plusDays(1).plusHours(1));
        booking.setAcceptedAt(now);

        BookingResponse response = mapper.toBookingResponse(booking);

        assertTrue(response.canPay());
        assertEquals(BookingNextAction.PAY_NOW, response.nextAction());
        assertEquals(BookingTime.toOffsetDateTime(now.plusMinutes(240)), response.actionDeadlineAt());
    }

    @Test
    void cancelledBeforePayment_exposesCancelledInsteadOfRefunded() {
        Booking booking = booking(BookingStatus.CANCELLED_BY_MENTEE,
                timeProvider.nowBusiness().plusDays(1), timeProvider.nowBusiness().plusDays(1).plusHours(1));

        BookingResponse response = mapper.toBookingResponse(booking);

        assertEquals(BookingPaymentStatus.CANCELLED, response.paymentStatus());
    }

    @Test
    void cancelledAfterPaymentBeforeRefund_remainsPaid() {
        Booking booking = booking(BookingStatus.CANCELLED_BY_MENTEE,
                timeProvider.nowBusiness().plusDays(1), timeProvider.nowBusiness().plusDays(1).plusHours(1));
        PaymentOrder order = paymentOrder(PaymentSettlementStatus.HELD);

        BookingResponse response = mapper.toBookingResponse(booking, null, null,
                Map.of(booking.getId(), order), null);

        assertEquals(BookingPaymentStatus.PAID, response.paymentStatus());
    }

    @Test
    void cancelledAfterPaymentAndRefund_exposesRefunded() {
        Booking booking = booking(BookingStatus.CANCELLED_BY_MENTEE,
                timeProvider.nowBusiness().plusDays(1), timeProvider.nowBusiness().plusDays(1).plusHours(1));
        PaymentOrder order = paymentOrder(PaymentSettlementStatus.REFUNDED);

        BookingResponse response = mapper.toBookingResponse(booking, null, null,
                Map.of(booking.getId(), order), null);

        assertEquals(BookingPaymentStatus.REFUNDED, response.paymentStatus());
    }

    @Test
    void failedPaymentOrder_isExposedWhileBookingRemainsAwaitingPayment() {
        Booking booking = booking(BookingStatus.ACCEPTED_AWAITING_PAYMENT,
                timeProvider.nowBusiness().plusDays(1), timeProvider.nowBusiness().plusDays(1).plusHours(1));
        PaymentOrder order = paymentOrder(null);
        order.setStatus(PaymentOrderStatus.FAILED);

        BookingResponse response = mapper.toBookingResponse(booking, null, null,
                Map.of(booking.getId(), order), null);

        assertEquals(BookingPaymentStatus.FAILED, response.paymentStatus());
    }

    @Test
    void expiredPendingRequest_doesNotExposeStaleDecisionCtaBeforeSchedulerRuns() {
        authenticate(mentorId, RoleCode.MENTOR);
        LocalDateTime now = timeProvider.nowBusiness();
        Booking booking = booking(BookingStatus.PENDING, now.plusDays(1), now.plusDays(1).plusHours(1));
        booking.setPendingExpireAt(now.minusSeconds(1));

        BookingResponse response = mapper.toBookingResponse(booking);

        assertFalse(response.canAccept());
        assertFalse(response.canReject());
        assertEquals(BookingNextAction.NONE, response.nextAction());
    }

    @Test
    void expiredPaymentWindow_doesNotExposeStalePaymentCtaBeforeSchedulerRuns() {
        authenticate(menteeId, RoleCode.MENTEE);
        LocalDateTime now = timeProvider.nowBusiness();
        Booking booking = booking(BookingStatus.ACCEPTED_AWAITING_PAYMENT,
                now.plusDays(1), now.plusDays(1).plusHours(1));
        booking.setAcceptedAt(now.minusMinutes(241));

        BookingResponse response = mapper.toBookingResponse(booking);

        assertFalse(response.canPay());
        assertEquals(BookingNextAction.NONE, response.nextAction());
    }

    @Test
    void paymentDeadline_usesUtcAtTheExactBoundaryEvenWhenLegacyFieldsDiffer() {
        authenticate(menteeId, RoleCode.MENTEE);
        LocalDateTime now = timeProvider.nowBusiness();
        Booking booking = booking(BookingStatus.ACCEPTED_AWAITING_PAYMENT,
                now.plusDays(1), now.plusDays(1).plusHours(1));
        booking.setAcceptedAt(now);
        booking.setAcceptedAtUtc(FIXED_NOW.minusSeconds(240 * 60));
        booking.setSelectedStartTimeUtc(FIXED_NOW.plusSeconds(6 * 60 * 60));

        BookingResponse response = mapper.toBookingResponse(booking);

        assertFalse(response.canPay());
        assertEquals(BookingNextAction.NONE, response.nextAction());
    }

    @Test
    void underReviewBooking_exposesCounterpartyAndAdminSlaDeadlines() {
        authenticate(menteeId, RoleCode.MENTEE);
        Booking booking = booking(BookingStatus.UNDER_REVIEW, timeProvider.nowBusiness().minusHours(4), timeProvider.nowBusiness().minusHours(3));
        Instant submittedUtc = FIXED_NOW.minusSeconds(2 * 60 * 60);
        Instant escalatedUtc = FIXED_NOW.minusSeconds(30 * 60);
        booking.setIssueSubmittedAtUtc(submittedUtc);
        booking.setIssueHumanReviewEscalatedAtUtc(escalatedUtc);

        BookingResponse response = mapper.toBookingResponse(booking);

        assertEquals(BookingTime.toOffsetDateTime(submittedUtc.plusSeconds(24 * 60 * 60)), response.issueResponseDeadlineAt());
        assertEquals(BookingTime.toOffsetDateTime(escalatedUtc.plusSeconds(48 * 60 * 60)), response.issueAdminResolutionDeadlineAt());
        assertEquals(BookingDisputeSlaStatus.WAITING_ADMIN, response.disputeSlaStatus());
        assertEquals(BookingNextAction.VIEW_ISSUE, response.nextAction());
    }

    @Test
    void completedBooking_withoutFeedback_menteeGetsLeaveFeedbackCta() {
        authenticate(menteeId, RoleCode.MENTEE);
        Booking booking = booking(BookingStatus.COMPLETED, timeProvider.nowBusiness().minusHours(2), timeProvider.nowBusiness().minusHours(1));
        booking.setCompletionOutcome(com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome.USER_CONFIRMED);

        BookingResponse response = mapper.toBookingResponse(booking);

        assertTrue(response.canSubmitFeedback());
        assertEquals(BookingDisplayState.FEEDBACK_REQUIRED, response.displayState());
        assertEquals(BookingNextAction.LEAVE_FEEDBACK, response.nextAction());
    }

    @Test
    void completedBooking_withFeedback_menteeGetsCompletedStateAndNoFeedbackCta() {
        authenticate(menteeId, RoleCode.MENTEE);
        Booking booking = booking(BookingStatus.COMPLETED, timeProvider.nowBusiness().minusHours(2), timeProvider.nowBusiness().minusHours(1));
        booking.setCompletionOutcome(com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome.USER_CONFIRMED);

        BookingResponse response = mapper.toBookingResponse(booking);

        assertTrue(response.canSubmitFeedback());
        assertEquals(BookingDisplayState.FEEDBACK_REQUIRED, response.displayState());
        assertEquals(BookingNextAction.LEAVE_FEEDBACK, response.nextAction());
    }

    @Test
    void autoClosedBooking_withoutFeedback_menteeGetsLeaveFeedbackCta() {
        authenticate(menteeId, RoleCode.MENTEE);
        Booking booking = booking(BookingStatus.COMPLETED, timeProvider.nowBusiness().minusHours(2), timeProvider.nowBusiness().minusHours(1));
        booking.setCompletionOutcome(com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome.AUTO_CLOSED);

        BookingResponse response = mapper.toBookingResponse(booking);

        assertTrue(response.canSubmitFeedback());
        assertEquals(BookingDisplayState.FEEDBACK_REQUIRED, response.displayState());
        assertEquals(BookingNextAction.LEAVE_FEEDBACK, response.nextAction());
    }

    private Booking booking(BookingStatus status, LocalDateTime start, LocalDateTime end) {
        User mentee = User.builder().id(menteeId).email("mentee@test.com").fullName("Mentee").build();
        User mentor = User.builder().id(mentorId).email("mentor@test.com").fullName("Mentor").build();
        return Booking.builder()
                .id(UUID.randomUUID())
                .menteeUserId(mentee.getId())
                .mentorUserId(mentorId)
                .serviceId(UUID.randomUUID())
                .status(status)
                .selectedStartTime(start)
                .selectedEndTime(end)
                .serviceIsFreeSnapshot(false)
                .servicePriceScoinSnapshot(30_000)
                .learningGoalTitle("Production booking flow")
                .createdAt(timeProvider.nowBusiness())
                .updatedAt(timeProvider.nowBusiness())
                .build();
    }

    private PaymentOrder paymentOrder(PaymentSettlementStatus settlementStatus) {
        return PaymentOrder.builder()
                .id(UUID.randomUUID())
                .targetId(UUID.randomUUID())
                .status(PaymentOrderStatus.PAID)
                .settlementStatus(settlementStatus)
                .build();
    }

    private void authenticate(UUID userId, RoleCode role) {
        UserPrincipal principal = UserPrincipal.create(userId, userId + "@test.com", List.of(role));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
