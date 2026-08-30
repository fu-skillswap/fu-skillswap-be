package com.fptu.exe.skillswap.modules.booking.port.dto;

import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionReasonCode;

public record BookingAdminReverseResolutionCommand(
        AdminBookingIssueResolutionReasonCode reasonCode,
        String adminNote
) {
}
