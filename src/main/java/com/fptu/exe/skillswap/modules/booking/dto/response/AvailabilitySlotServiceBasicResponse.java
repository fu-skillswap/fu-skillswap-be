package com.fptu.exe.skillswap.modules.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Thông tin service cơ bản được gắn vào availability slot")
public record AvailabilitySlotServiceBasicResponse(
        @Schema(description = "ID của service mà mentee sẽ truyền tiếp vào API candidates hoặc create booking")
        UUID serviceId,
        @Schema(description = "Tên service")
        String title,
        @Schema(description = "Thời lượng thực của service tính theo phút. Exact candidate segments sẽ được cắt theo duration này.")
        Integer durationMinutes,
        @Schema(description = "Service miễn phí hay trả phí")
        boolean isFree,
        @Schema(description = "Đơn giá service theo Scoin nếu không miễn phí")
        Integer priceScoin
) {
}
