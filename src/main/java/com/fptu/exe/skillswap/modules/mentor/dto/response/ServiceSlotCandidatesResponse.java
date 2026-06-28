package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Danh sách exact candidate segments của một service trong một parent availability slot")
public record ServiceSlotCandidatesResponse(
        @Schema(description = "ID parent slot mà mentee đã chọn")
        UUID slotId,
        @Schema(description = "serviceId được chọn từ danh sách services của parent slot")
        UUID serviceId,
        @Schema(description = "Duration thực của service. Exact candidate segments bên dưới luôn có độ dài bằng giá trị này.")
        Integer serviceDurationMinutes,
        List<ServiceSlotCandidateItemResponse> candidateServiceSlots
) {
}
