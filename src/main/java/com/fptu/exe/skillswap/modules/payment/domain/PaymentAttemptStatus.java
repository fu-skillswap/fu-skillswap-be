package com.fptu.exe.skillswap.modules.payment.domain;

public enum PaymentAttemptStatus {
    PENDING,
    REDIRECTED,
    SUCCEEDED,
    SUCCEEDED_SURPLUS,
    FAILED,
    CANCELLED,
    EXPIRED
}
