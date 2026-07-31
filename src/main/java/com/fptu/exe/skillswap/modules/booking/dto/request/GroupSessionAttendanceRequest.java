package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.GroupAttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record GroupSessionAttendanceRequest(
        @NotNull Integer expectedVersion,
        @NotEmpty List<@Valid Attendee> attendees
) {
    public record Attendee(@NotNull UUID bookingId, @NotNull GroupAttendanceStatus status) {}
}
