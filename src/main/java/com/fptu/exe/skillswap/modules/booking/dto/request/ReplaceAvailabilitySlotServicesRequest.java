package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(description = "Payload để mentor thay toàn bộ danh sách service được gắn vào một availability slot")
public record ReplaceAvailabilitySlotServicesRequest(
        @Schema(description = "Danh sách serviceId sẽ được gắn vào slot. Có thể truyền mảng rỗng để gỡ toàn bộ service khỏi slot.", example = "[\"019f3234-aaaa-bbbb-cccc-1234567890ab\"]")
        @NotNull(message = "serviceIds không được để trống")
        List<UUID> serviceIds
) {
}
