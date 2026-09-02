package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentQueryPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSnapshot;
import com.fptu.exe.skillswap.modules.booking.port.BookingSettlementSnapshot;
import com.fptu.exe.skillswap.modules.booking.port.BookingIssueResolutionSnapshot;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionKind;
import com.fptu.exe.skillswap.modules.booking.repository.BookingIssueResolutionRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Booking-owned adapter that prevents payment consumers from seeing Booking persistence types. */
@Service
@RequiredArgsConstructor
public class BookingPaymentQueryAdapter implements BookingPaymentQueryPort {
    private final BookingRepository bookingRepository;
    private final BookingIssueResolutionRepository bookingIssueResolutionRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingPaymentSnapshot> findPaymentSnapshot(UUID bookingId) {
        if (bookingId == null) {
            return Optional.empty();
        }
        return bookingRepository.findById(bookingId).map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingSettlementSnapshot> findSettlementSnapshot(UUID bookingId) {
        if (bookingId == null) {
            return Optional.empty();
        }
        return bookingRepository.findById(bookingId).map(booking -> {
            BookingPaymentSnapshot payment = toSnapshot(booking);
            var resolution = bookingIssueResolutionRepository
                    .findFirstByBookingIdAndResolutionKindOrderByCreatedAtUtcDesc(bookingId, BookingIssueResolutionKind.RESOLUTION)
                    .map(this::toResolutionSnapshot)
                    .orElse(null);
            boolean eligible = "COMPLETED".equals(payment.paymentStatus())
                    && ("USER_CONFIRMED".equals(paymentStatusOutcome(booking))
                    || "AUTO_CLOSED".equals(paymentStatusOutcome(booking))
                    || "NO_SHOW_MENTEE".equals(paymentStatusOutcome(booking))
                    || "ADMIN_SLA_AUTO_RELEASED".equals(paymentStatusOutcome(booking)));
            return new BookingSettlementSnapshot(payment.bookingId(), payment.payerUserId(), payment.mentorUserId(),
                    payment.amountScoin(), payment.paymentStatus(), paymentStatusOutcome(booking), resolution, eligible,
                    payment.selectedStartAtUtc(), payment.paymentExpiresAtUtc());
        });
    }

    private String paymentStatusOutcome(Booking booking) {
        return booking.getCompletionOutcome() == null ? null : booking.getCompletionOutcome().name();
    }

    private BookingIssueResolutionSnapshot toResolutionSnapshot(com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolution resolution) {
        return new BookingIssueResolutionSnapshot(
                resolution.getId(),
                resolution.getAction() == null ? null : resolution.getAction().name(),
                resolution.getReasonCode() == null ? null : resolution.getReasonCode().name(),
                resolution.getStatus() == null ? null : resolution.getStatus().name(),
                resolution.getMenteeBps(), resolution.getMentorBps(), resolution.getPlatformBps(),
                resolution.getEscrowScoin(), resolution.getMenteeRefundScoin(),
                resolution.getMentorSettlementScoin(), resolution.getPlatformSettlementScoin(),
                resolution.getSettlementAppliedAtUtc(), resolution.getReversalOfResolutionId());
    }

    private BookingPaymentSnapshot toSnapshot(Booking booking) {
        Instant selectedStartAtUtc = booking.getSelectedStartTimeUtc();
        if (selectedStartAtUtc == null && booking.getSlot() != null) {
            selectedStartAtUtc = booking.getSlot().getStartTimeUtc();
        }
        if (selectedStartAtUtc == null) {
            selectedStartAtUtc = booking.getSelectedStartTime() == null
                    ? null
                    : com.fptu.exe.skillswap.shared.time.BusinessTime.toInstant(booking.getSelectedStartTime());
        }

        Instant acceptedAtUtc = booking.getAcceptedAtUtc();
        if (acceptedAtUtc == null && booking.getAcceptedAt() != null) {
            acceptedAtUtc = com.fptu.exe.skillswap.shared.time.BusinessTime.toInstant(booking.getAcceptedAt());
        }

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
                BookingDeadlinePolicy.resolvePaymentDeadline(acceptedAtUtc, selectedStartAtUtc)
        );
    }
}
