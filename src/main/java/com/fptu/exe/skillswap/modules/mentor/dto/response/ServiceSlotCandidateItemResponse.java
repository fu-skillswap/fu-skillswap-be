package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Một exact candidate segment cụ thể được tính toán từ parent slot và duration của service. Đây là đơn vị thật sự dùng để queue, accept và reject booking.")
public record ServiceSlotCandidateItemResponse(
        @Schema(description = "Thời gian bắt đầu exact segment")
        LocalDateTime startTime,
        @Schema(description = "Thời gian kết thúc exact segment")
        LocalDateTime endTime,
        @Schema(description = "Số request PENDING hiện có đúng trên exact segment này, tính chung trong slot")
        int pendingCount,
        @Schema(description = "Số quota PENDING còn lại trên exact segment này")
        int remainingPendingQuota,
        @Schema(description = "FE chỉ được cho user chọn khi giá trị này là true")
        boolean isSelectable,
        @Schema(description = "Lý do tổng quát nếu segment không thể chọn")
        String reasonIfBlocked,
        @Schema(description = "true nếu segment đang bị block bởi ít nhất một booking ACCEPTED đang overlap")
        boolean blockedByAcceptedBooking,
        @Schema(description = "bookingId ACCEPTED đầu tiên đang block segment này, nếu có", nullable = true)
        UUID blockingBookingId,
        @Schema(description = "serviceId của booking ACCEPTED đang block segment này, nếu có", nullable = true)
        UUID blockingServiceId,
        @Schema(description = "Tiêu đề service của booking ACCEPTED đang block segment này, nếu có", nullable = true)
        String blockingServiceTitle,
        @Schema(description = "true nếu booking ACCEPTED đang block thuộc cùng service mà FE đang query")
        boolean blockedBySameService,
        @Schema(description = "true nếu booking ACCEPTED đang block thuộc service khác service mà FE đang query")
        boolean blockedByDifferentService,
        @Schema(description = "Note rõ nghĩa cho FE: segment đã bị đặt bởi cùng service hay service khác", nullable = true)
        String bookingConflictNote
) {
}
