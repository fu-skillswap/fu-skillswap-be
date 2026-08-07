package com.fptu.exe.skillswap.modules.course.domain;

/** Immutable per-session escrow allocation lifecycle. */
public enum CourseSettlementStatus {
    HELD,
    ELIGIBLE,
    RELEASED,
    REFUNDED
}
