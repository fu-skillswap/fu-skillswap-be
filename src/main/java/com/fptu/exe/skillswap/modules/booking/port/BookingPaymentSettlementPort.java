package com.fptu.exe.skillswap.modules.booking.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Booking-owned read boundary for payment cancellation and settlement workflows.
 * Only immutable contract values cross this boundary.
 */
public interface BookingPaymentSettlementPort {

    Optional<BookingCancellationContext> findCancellationContext(UUID bookingId);

    Optional<BookingSettlementSnapshot> findSettlementSnapshot(UUID bookingId);

    Optional<BookingIssueResolutionSnapshot> findIssueResolution(UUID bookingId, UUID resolutionId);

    void updateIssueResolutionSettlement(UUID bookingId,
                                         UUID resolutionId,
                                         BookingIssueResolutionSettlementUpdate update);

    Optional<BookingPaymentSnapshot> findPaymentSnapshotForUpdate(UUID bookingId);

    void confirmPayment(UUID bookingId, Instant confirmedAtUtc);

    void expirePayment(UUID bookingId, Instant expiredAtUtc, String reason);

    void ensurePaidSideEffects(UUID bookingId);
}
