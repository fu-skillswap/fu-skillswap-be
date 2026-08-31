package com.fptu.exe.skillswap.modules.payment.support;

import java.util.UUID;

/** Test fixture providing standardized payment order, credit ledger, and checkout snapshots. */
public final class PaymentTestFixture {

    private PaymentTestFixture() {}

    public static UUID randomPaymentId() {
        return UUID.randomUUID();
    }

    public static PaymentOrderSnapshot createOrderSnapshot(UUID bookingId, UUID userId, long amount) {
        return new PaymentOrderSnapshot(
                UUID.randomUUID(),
                bookingId != null ? bookingId : UUID.randomUUID(),
                userId != null ? userId : UUID.randomUUID(),
                amount > 0 ? amount : 50000L,
                "COMPLETED",
                "PAYOS",
                "ORDER-" + System.currentTimeMillis()
        );
    }

    public record PaymentOrderSnapshot(
            UUID paymentOrderId,
            UUID bookingId,
            UUID userId,
            long amountVnd,
            String status,
            String provider,
            String orderCode
    ) {}
}
