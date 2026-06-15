package com.fptu.exe.skillswap.modules.booking.dto;

import jakarta.validation.constraints.Size;

public record AcceptBookingRequest(
        @Size(max = 2000, message = "mentorResponseNote không được vượt quá 2000 ký tự")
        String mentorResponseNote
) {
}
