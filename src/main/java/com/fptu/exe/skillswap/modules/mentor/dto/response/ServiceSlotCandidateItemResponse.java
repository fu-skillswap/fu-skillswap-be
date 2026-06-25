package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@Schema(description = "Một exact candidate segment cụ thể được tính toán từ parent slot và duration của service. Đây là đơn vị thật sự dùng để queue, accept và reject booking.")
public record ServiceSlotCandidateItemResponse(
        LocalDateTime startTime,
        LocalDateTime endTime,
        int pendingCount,
        int remainingPendingQuota,
        boolean isSelectable,
        String reasonIfBlocked
) {
}
