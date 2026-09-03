package com.fptu.exe.skillswap.modules.payment.port;

/**
 * Public payment state needed by other modules without exposing payment entities
 * or payment-domain enums.
 */
public record PaymentStatusContract(
        String orderStatus,
        String settlementStatus
) {
}
