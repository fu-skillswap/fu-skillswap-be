package com.fptu.exe.skillswap.modules.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record DeactivateAvailabilitySlotRequest(
        @NotNull(message = "expectedVersion là bắt buộc")
        @PositiveOrZero(message = "expectedVersion không hợp lệ")
        Integer expectedVersion,
        Boolean rejectPendingBookings,
        String pendingRejectionToken,
        Integer expectedTemplateVersion
) {}
