package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Quyền hạn thay đổi cấu trúc slot của mentor")
public record SlotMutationCapabilityResponse(
        @Schema(description = "Mode mutation tương ứng")
        SlotMutationMode mode,
        @Schema(description = "Lý do nếu không được phép mutation")
        String restrictionCode,
        @Schema(description = "Số lượng booking pending bị ảnh hưởng")
        int affectedPendingBookingCount
) {}
