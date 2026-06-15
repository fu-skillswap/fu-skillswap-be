package com.fptu.exe.skillswap.modules.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateBookingRequest(
        @NotNull(message = "mentorUserId là bắt buộc")
        UUID mentorUserId,

        @NotNull(message = "availabilitySlotId là bắt buộc")
        UUID availabilitySlotId,

        UUID serviceId,

        @NotBlank(message = "learningGoalTitle không được để trống")
        @Size(max = 200, message = "learningGoalTitle không được vượt quá 200 ký tự")
        String learningGoalTitle,

        @Size(max = 2000, message = "learningGoalDescription không được vượt quá 2000 ký tự")
        String learningGoalDescription
) {
}
