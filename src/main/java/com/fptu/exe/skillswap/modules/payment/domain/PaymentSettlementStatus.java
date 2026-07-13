package com.fptu.exe.skillswap.modules.payment.domain;

/**
 * Tracks where captured booking funds are in the marketplace settlement flow.
 * Payment collection and payout/refund are intentionally separate lifecycles.
 */
public enum PaymentSettlementStatus {
    HELD,
    RELEASED,
    REFUNDED
}
