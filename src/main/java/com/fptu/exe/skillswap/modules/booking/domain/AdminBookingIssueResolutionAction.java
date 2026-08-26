package com.fptu.exe.skillswap.modules.booking.domain;

public enum AdminBookingIssueResolutionAction {
    CONFIRM_SESSION,
    /** Rejects a non-no-show dispute and applies the normal booking allocation. */
    RELEASE_AS_IS,
    /** Splits the captured escrow explicitly between mentee, mentor, and platform. */
    PARTIAL_SETTLEMENT,
    CONFIRM_MENTOR_NO_SHOW_REFUND,
    CONFIRM_MENTEE_NO_SHOW_RELEASE
}
