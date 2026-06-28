package com.fptu.exe.skillswap.modules.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Slot availability của mentor sau khi backend cập nhật danh sách service được gắn vào slot")
public record MentorManagedAvailabilitySlotResponse(
        UUID slotId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String timezone,
        boolean active,
        boolean booked,
        String note,
        List<AvailabilitySlotServiceBasicResponse> services
) {
}
