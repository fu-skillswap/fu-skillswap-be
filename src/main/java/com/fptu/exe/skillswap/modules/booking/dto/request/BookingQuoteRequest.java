package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fptu.exe.skillswap.shared.time.FlexibleInstantDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Read-only quote cho exact availability candidate trước khi tạo booking")
public record BookingQuoteRequest(
        @Schema(description = "ID của availability slot mà người dùng đã chọn", example = "019f4234-aaaa-bbbb-cccc-1234567890ab")
        @NotNull UUID slotId,
        @Schema(description = "ID dịch vụ mentor được gắn với slot đã chọn", example = "019f4234-bbbb-cccc-dddd-1234567890ab")
        @NotNull UUID serviceId,
        @Schema(description = "Thời điểm bắt đầu candidate segment theo ISO-8601. Gửi đúng timezone hiển thị trong discovery.", example = "2026-07-15T09:00:00+07:00")
        @NotNull @JsonDeserialize(using = FlexibleInstantDeserializer.class) Instant startAt
) {
}
