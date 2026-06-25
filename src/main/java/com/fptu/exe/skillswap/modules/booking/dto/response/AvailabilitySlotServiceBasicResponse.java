package com.fptu.exe.skillswap.modules.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin service cơ bản được gắn vào availability slot")
public record AvailabilitySlotServiceBasicResponse(
        UUID serviceId,
        String title,
        Integer durationMinutes,
        boolean isFree,
        BigDecimal priceAmount,
        String currency
) {
}
