package com.fptu.exe.skillswap.modules.booking.domain;

public enum BookingIssueType {
    MENTOR_NO_SHOW,
    MENTEE_NO_SHOW,
    QUALITY_ISSUE,
    TECHNICAL_PROBLEM,
    OTHER,
    /** Legacy value kept readable during the migration window. */
    @Deprecated
    NO_SHOW_OR_QUALITY_OR_OTHER
}
