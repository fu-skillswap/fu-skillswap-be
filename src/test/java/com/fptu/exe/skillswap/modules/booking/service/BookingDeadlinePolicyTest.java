package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.service.BookingDeadlinePolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookingDeadlinePolicyTest {

    @Test
    void pendingExpiryUsesTheEarlierResponseOrPreparationDeadline() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 1, 8, 0);

        assertEquals(
                LocalDateTime.of(2026, 8, 1, 20, 0),
                BookingDeadlinePolicy.resolvePendingExpiry(createdAt, LocalDateTime.of(2026, 8, 2, 8, 0)));
        assertEquals(
                LocalDateTime.of(2026, 8, 1, 10, 0),
                BookingDeadlinePolicy.resolvePendingExpiry(createdAt, LocalDateTime.of(2026, 8, 1, 13, 0)));
    }

    @Test
    void paymentDeadlineUsesTheEarlierOneHourWindowOrPreparationBuffer() {
        LocalDateTime acceptedAt = LocalDateTime.of(2026, 8, 1, 8, 0);

        assertEquals(
                LocalDateTime.of(2026, 8, 1, 9, 0),
                BookingDeadlinePolicy.resolvePaymentDeadline(acceptedAt, LocalDateTime.of(2026, 8, 2, 8, 0)));
        assertEquals(
                LocalDateTime.of(2026, 8, 1, 8, 30),
                BookingDeadlinePolicy.resolvePaymentDeadline(acceptedAt, LocalDateTime.of(2026, 8, 1, 9, 30)));
    }
}
