package com.fptu.exe.skillswap.modules.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelBookingRequest(
        @NotBlank(message = "cancelReason không được để trống")
        @Size(max = 1000, message = "cancelReason không được vượt quá 1000 ký tự")
        String cancelReason
) {
}
