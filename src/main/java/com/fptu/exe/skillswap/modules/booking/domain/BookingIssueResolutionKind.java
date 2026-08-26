package com.fptu.exe.skillswap.modules.booking.domain;

/** Append-only audit record kind. Reversal support is intentionally kept distinct from a decision. */
public enum BookingIssueResolutionKind {
    RESOLUTION,
    REVERSAL
}
