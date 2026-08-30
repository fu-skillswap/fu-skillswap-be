package com.fptu.exe.skillswap.modules.booking.port.dto;

import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction;
import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionReasonCode;

public record BookingAdminResolveIssueCommand(
        AdminBookingIssueResolutionAction action,
        AdminBookingIssueResolutionReasonCode reasonCode,
        String adminNote,
        Integer menteeBps,
        Integer mentorBps,
        Integer platformBps
) {
    public BookingAdminResolveIssueCommand(AdminBookingIssueResolutionAction action, String adminNote) {
        this(action, defaultReason(action), adminNote, null, null, null);
    }

    private static AdminBookingIssueResolutionReasonCode defaultReason(AdminBookingIssueResolutionAction action) {
        if (action == AdminBookingIssueResolutionAction.CONFIRM_MENTOR_NO_SHOW_REFUND
                || action == AdminBookingIssueResolutionAction.CONFIRM_MENTEE_NO_SHOW_RELEASE) {
            return AdminBookingIssueResolutionReasonCode.NO_SHOW_CONFIRMED;
        }
        return AdminBookingIssueResolutionReasonCode.SESSION_CONFIRMED;
    }
}
