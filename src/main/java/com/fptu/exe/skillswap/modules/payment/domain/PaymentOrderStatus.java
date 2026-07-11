package com.fptu.exe.skillswap.modules.payment.domain;

public enum PaymentOrderStatus {
    PENDING,
    PARTIALLY_COVERED_BY_CREDIT,
    AWAITING_PROVIDER_PAYMENT,
    PAID,
    FAILED,
    CANCELLED,
    EXPIRED
}
