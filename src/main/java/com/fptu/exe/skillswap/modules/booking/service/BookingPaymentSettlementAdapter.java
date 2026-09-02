package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolution;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionExecutor;
import com.fptu.exe.skillswap.modules.booking.event.BookingCalendarLifecycleEvent;
import com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent;
import com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent;
import com.fptu.exe.skillswap.modules.booking.port.BookingCancellationContext;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentQueryPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSettlementPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingIssueResolutionSnapshot;
import com.fptu.exe.skillswap.modules.booking.port.BookingIssueResolutionSettlementUpdate;
import com.fptu.exe.skillswap.modules.booking.port.BookingSettlementSnapshot;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSnapshot;
import com.fptu.exe.skillswap.modules.booking.port.BookingChatPort;
import com.fptu.exe.skillswap.modules.booking.service.SessionService;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingIssueResolutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Booking-owned adapter for payment cancellation and settlement context. */
@Service
@RequiredArgsConstructor
public class BookingPaymentSettlementAdapter implements BookingPaymentSettlementPort {

    private final BookingRepository bookingRepository;
    private final BookingIssueResolutionRepository bookingIssueResolutionRepository;
    private final BookingPaymentQueryPort bookingPaymentQueryPort;
    private final SessionService sessionService;
    private final BookingChatPort bookingChatPort;
    private final UserQueryPort userQueryPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Optional<BookingCancellationContext> findCancellationContext(UUID bookingId) {
        return bookingRepository.findById(bookingId).map(this::toCancellationContext);
    }

    @Override
    public Optional<BookingSettlementSnapshot> findSettlementSnapshot(UUID bookingId) {
        return bookingPaymentQueryPort.findSettlementSnapshot(bookingId);
    }

    @Override
    public Optional<BookingIssueResolutionSnapshot> findIssueResolution(UUID bookingId, UUID resolutionId) {
        return bookingRepository.findById(bookingId)
                .flatMap(booking -> bookingIssueResolutionRepository.findById(resolutionId)
                        .filter(resolution -> bookingId.equals(resolution.getBookingId())))
                .map(resolution -> new BookingIssueResolutionSnapshot(
                        resolution.getId(),
                        resolution.getAction() == null ? null : resolution.getAction().name(),
                        resolution.getReasonCode() == null ? null : resolution.getReasonCode().name(),
                        resolution.getStatus() == null ? null : resolution.getStatus().name(),
                        resolution.getMenteeBps(), resolution.getMentorBps(), resolution.getPlatformBps(),
                        resolution.getEscrowScoin(), resolution.getMenteeRefundScoin(),
                        resolution.getMentorSettlementScoin(), resolution.getPlatformSettlementScoin(),
                        resolution.getSettlementAppliedAtUtc(), resolution.getReversalOfResolutionId()));
    }

    @Override
    public void updateIssueResolutionSettlement(UUID bookingId,
                                                UUID resolutionId,
                                                BookingIssueResolutionSettlementUpdate update) {
        BookingIssueResolution resolution = bookingRepository.findById(bookingId)
                .flatMap(booking -> bookingIssueResolutionRepository.findById(resolutionId)
                        .filter(candidate -> bookingId.equals(candidate.getBookingId())))
                .orElseThrow(() -> new IllegalArgumentException("Booking issue resolution không tồn tại"));

        resolution.setStatus(com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionStatus.valueOf(update.status()));
        resolution.setEscrowScoin(update.escrowScoin());
        resolution.setMenteeRefundScoin(update.menteeRefundScoin());
        resolution.setMentorSettlementScoin(update.mentorSettlementScoin());
        resolution.setPlatformSettlementScoin(update.platformSettlementScoin());
        resolution.setSettlementAppliedAtUtc(update.settlementAppliedAtUtc());
        bookingIssueResolutionRepository.save(resolution);
    }

    @Override
    public Optional<BookingPaymentSnapshot> findPaymentSnapshotForUpdate(UUID bookingId) {
        if (bookingId == null) {
            return Optional.empty();
        }
        return bookingRepository.findByIdForSessionUpdate(bookingId).map(this::toPaymentSnapshot);
    }

    @Override
    public void confirmPayment(UUID bookingId, Instant confirmedAtUtc) {
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy booking để xác nhận thanh toán"));
        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.PAYMENT_CONFIRMED, confirmedAtUtc);
        Booking savedBooking = bookingRepository.save(booking);
        createPaidSideEffects(savedBooking, confirmedAtUtc);
    }

    @Override
    public void expirePayment(UUID bookingId, Instant expiredAtUtc, String reason) {
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy booking để hết hạn thanh toán"));
        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.EXPIRE_PAYMENT, expiredAtUtc);
        booking.setRejectReason(reason);
        bookingRepository.save(booking);
    }

    @Override
    public void ensurePaidSideEffects(UUID bookingId) {
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy booking để tạo side effect thanh toán"));
        createSessionAndConversation(booking);
    }

    private BookingPaymentSnapshot toPaymentSnapshot(Booking booking) {
        Instant selectedStartAtUtc = booking.getSelectedStartTimeUtc();
        if (selectedStartAtUtc == null && booking.getSlot() != null) {
            selectedStartAtUtc = booking.getSlot().getStartTimeUtc();
        }
        if (selectedStartAtUtc == null) {
            selectedStartAtUtc = BookingTime.toInstant(booking.getSelectedStartTime());
        }
        Instant acceptedAtUtc = booking.getAcceptedAtUtc() != null
                ? booking.getAcceptedAtUtc() : BookingTime.toInstant(booking.getAcceptedAt());
        return new BookingPaymentSnapshot(
                booking.getId(),
                booking.getMenteeUserId(),
                booking.getMentorUserId(),
                booking.getServiceId(),
                booking.getServicePriceScoinSnapshot(),
                Boolean.TRUE.equals(booking.getServiceIsFreeSnapshot()),
                booking.getStatus() == null ? null : booking.getStatus().name(),
                acceptedAtUtc,
                selectedStartAtUtc,
                BookingDeadlinePolicy.resolvePaymentDeadline(acceptedAtUtc, selectedStartAtUtc));
    }

    private void createPaidSideEffects(Booking booking, Instant eventAtUtc) {
        createSessionAndConversation(booking);
        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                booking.getId(), booking.getMenteeUserId(), booking.getMentorUserId(), booking.getStatus(),
                "Thanh toán thành công. Lịch học đã được xác nhận.",
                booking.getUpdatedAt() != null ? booking.getUpdatedAt() : BookingTime.fromInstant(eventAtUtc)));
        eventPublisher.publishEvent(BookingCalendarLifecycleEvent.of(
                booking.getId(), booking.getMentorUserId(), BookingCalendarLifecycleEvent.Action.CREATE));
        if (booking.getMentorUserId() != null) {
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.NotificationEvent(
                    booking.getMentorUserId(),
                    com.fptu.exe.skillswap.modules.notification.NotificationType.BOOKING_PAYMENT_CONFIRMED,
                    "Mentee đã hoàn tất thanh toán và lịch đã được xác nhận",
                    menteeName(booking) + " đã hoàn tất thanh toán cho lịch mentoring với bạn.",
                    "BOOKING", booking.getId()));
        }
        var mentorUser = booking.getMentorUserId() != null && userQueryPort != null
                ? userQueryPort.findUserSummaryById(booking.getMentorUserId()).orElse(null) : null;
        eventPublisher.publishEvent(BookingEmailNotificationEvent.builder()
                .bookingId(booking.getId())
                .eventType(BookingEmailNotificationEvent.EventType.BOOKING_PAID_CONFIRMED_EMAIL)
                .recipientEmail(mentorUser == null ? null : mentorUser.email())
                .recipientName(mentorUser == null ? null : mentorUser.fullName())
                .actorName(menteeName(booking))
                .bookingStartTime(booking.getSelectedStartTime())
                .bookingEndTime(booking.getSelectedEndTime())
                .learningGoalTitle(booking.getLearningGoalTitle())
                .learningGoalDescription(booking.getLearningGoalDescription())
                .serviceTitle(booking.getServiceTitleSnapshot())
                .serviceDurationMinutes(booking.getServiceDurationSnapshot())
                .serviceFree(booking.getServiceIsFreeSnapshot())
                .servicePriceScoin(booking.getServicePriceScoinSnapshot())
                .serviceExpectedOutcome(booking.getServiceExpectedOutcomeSnapshot())
                .mentorResponseNote(booking.getMentorResponseNote())
                .createdAt(BookingTime.fromInstant(eventAtUtc))
                .build());
    }

    private void createSessionAndConversation(Booking booking) {
        if (sessionService != null) {
            sessionService.createForAcceptedBooking(booking);
        }
        if (bookingChatPort != null) {
            bookingChatPort.createDirectForAcceptedBooking(
                    booking.getId(), booking.getMentorUserId(), booking.getMenteeUserId());
        }
    }

    private BookingCancellationContext toCancellationContext(Booking booking) {
        Instant acceptedAtUtc = booking.getAcceptedAtUtc() != null
                ? booking.getAcceptedAtUtc() : BookingTime.toInstant(booking.getAcceptedAt());
        Instant selectedStartAtUtc = booking.getSelectedStartTimeUtc() != null
                ? booking.getSelectedStartTimeUtc() : BookingTime.toInstant(booking.getSelectedStartTime());
        Instant cancelledAtUtc = booking.getCancelledAtUtc() != null
                ? booking.getCancelledAtUtc() : BookingTime.toInstant(booking.getCancelledAt());

        long minutesUntilStart = selectedStartAtUtc == null || cancelledAtUtc == null
                ? Long.MAX_VALUE : Duration.between(cancelledAtUtc, selectedStartAtUtc).toMinutes();
        boolean cancelled = booking.getStatus() == BookingStatus.CANCELLED_BY_MENTEE
                || booking.getStatus() == BookingStatus.CANCELLED_BY_MENTOR;

        return new BookingCancellationContext(
                booking.getId(),
                booking.getMenteeUserId(),
                booking.getMentorUserId(),
                booking.getStatus() == null ? null : booking.getStatus().name(),
                booking.getCancelReason(),
                acceptedAtUtc,
                selectedStartAtUtc,
                cancelledAtUtc,
                BookingDeadlinePolicy.isLateCancellation(minutesUntilStart),
                cancelled && acceptedAtUtc != null
        );
    }

    private String menteeName(Booking booking) {
        if (booking == null || booking.getMenteeUserId() == null || userQueryPort == null) return "Mentee";
        return userQueryPort.findUserSummaryById(booking.getMenteeUserId())
                .map(com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord::fullName)
                .orElse("Mentee");
    }
}
