package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Danh sách exact candidate segments của một service trong một parent availability slot")
public record ServiceSlotCandidatesResponse(
        UUID slotId,
        UUID serviceId,
        Integer serviceDurationMinutes,
        List<ServiceSlotCandidateItemResponse> candidateServiceSlots
) {
}
