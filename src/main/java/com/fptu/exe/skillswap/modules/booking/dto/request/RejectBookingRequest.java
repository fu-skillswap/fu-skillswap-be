package com.fptu.exe.skillswap.modules.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectBookingRequest(
        @NotBlank(message = "rejectReason không được để trống")
        @Size(max = 2000, message = "rejectReason không được vượt quá 2000 ký tự")
        String rejectReason,

        @Size(max = 2000, message = "mentorResponseNote không được vượt quá 2000 ký tự")
        String mentorResponseNote
) {
}
