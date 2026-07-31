package com.fptu.exe.skillswap.modules.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupSessionBookingRequest(
        @NotBlank @Size(max = 200) String learningGoalTitle,
        @Size(max = 2000) String learningGoalDescription
) {}
