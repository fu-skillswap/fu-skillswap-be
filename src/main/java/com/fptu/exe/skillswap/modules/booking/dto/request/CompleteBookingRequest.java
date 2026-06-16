package com.fptu.exe.skillswap.modules.booking.dto.request;

import jakarta.validation.constraints.Size;

public record CompleteBookingRequest(
        @Size(max = 2000, message = "completionNote không được vượt quá 2000 ký tự")
        String completionNote
) {
}
