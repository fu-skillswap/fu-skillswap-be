package com.fptu.exe.skillswap.modules.booking.domain;

/**
 * Stable, reportable reasons for an administrator's dispute decision. The optional admin note
 * explains the case; this code is deliberately short enough for reporting and analytics.
 */
public enum AdminBookingIssueResolutionReasonCode {
    SESSION_CONFIRMED,
    NO_SHOW_CONFIRMED,
    EVIDENCE_INSUFFICIENT,
    QUALITY_PARTIAL_COMPENSATION,
    TECHNICAL_PARTIAL_COMPENSATION,
    OTHER
}
