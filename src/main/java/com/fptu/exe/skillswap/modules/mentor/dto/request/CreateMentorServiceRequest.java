package com.fptu.exe.skillswap.modules.mentor.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;

public record CreateMentorServiceRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 1000) String description,
        @NotBlank @Size(max = 1000) String expectedOutcome,
        @NotNull Integer durationMinutes,
        @NotNull Boolean isFree,
        @NotNull @Min(0) @Max(45_000_000) Integer priceScoin,
        Boolean maintainPostSessionChat,
        MentorServiceDeliveryMode deliveryMode
) {}
