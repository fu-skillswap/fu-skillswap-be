package com.fptu.exe.skillswap.modules.booking.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, payment-facing projection of a booking.
 *
 * <p>This contract deliberately contains no Booking entity, repository type,
 * domain enum, or persistence relationship.</p>
 */
public record BookingPaymentSnapshot(
        UUID bookingId,
        UUID payerUserId,
        UUID mentorUserId,
        UUID serviceId,
        Integer amountScoin,
        boolean free,
        String paymentStatus,
        Instant acceptedAtUtc,
        Instant selectedStartAtUtc,
        Instant paymentExpiresAtUtc
) {
}
