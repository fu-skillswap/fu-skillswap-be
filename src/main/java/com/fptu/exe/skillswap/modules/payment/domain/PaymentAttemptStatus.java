package com.fptu.exe.skillswap.modules.payment.domain;

public enum PaymentAttemptStatus {
    /** Persisted before the payment provider is contacted. */
    CREATING,
    /** The provider link is durable and can be returned to the payer. */
    CREATED,
    PENDING,
    REDIRECTED,
    SUCCEEDED,
    SUCCEEDED_SURPLUS,
    FAILED,
    CANCELLED,
    EXPIRED
}
