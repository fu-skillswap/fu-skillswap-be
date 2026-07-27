package com.fptu.exe.skillswap.modules.booking.dto.response;

public record SlotMutationCapabilityResponse(
        SlotMutationMode mode,
        String restrictionCode,
        int affectedPendingBookingCount
) {}
