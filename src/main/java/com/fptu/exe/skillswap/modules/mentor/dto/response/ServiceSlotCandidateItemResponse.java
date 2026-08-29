package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Một exact candidate segment cụ thể được tính toán từ parent slot và duration của service. Đây là đơn vị thật sự dùng để queue, accept và reject booking.")
public record ServiceSlotCandidateItemResponse(
        @Schema(description = "Thời gian bắt đầu giờ địa phương Việt Nam (để FE hiển thị lên giao diện)", example = "2026-08-30T18:16:00")
        LocalDateTime startTime,
        @Schema(description = "Thời gian kết thúc giờ địa phương Việt Nam (để FE hiển thị lên giao diện)", example = "2026-08-30T18:46:00")
        LocalDateTime endTime,
        @Schema(description = "Thời gian bắt đầu chuẩn UTC Instant (để FE truyền thẳng vào POST /api/bookings startAt nếu muốn)", example = "2026-08-30T11:16:00Z")
        Instant startAt,
        @Schema(description = "Thời gian kết thúc chuẩn UTC Instant", example = "2026-08-30T11:46:00Z")
        Instant endAt,
        @Schema(description = "Số request PENDING hiện có đúng trên exact segment này, tính chung trong slot")
        int pendingCount,
        @Schema(description = "Số quota PENDING còn lại trên exact segment này")
        int remainingPendingQuota,
        @Schema(description = "FE chỉ được cho user chọn khi giá trị này là true")
        boolean isSelectable,
        @Schema(description = "Lý do tổng quát nếu segment không thể chọn")
        String reasonIfBlocked,
        @Schema(description = "true nếu segment đang bị block bởi ít nhất một booking đã được chốt đang overlap")
        boolean blockedByAcceptedBooking,
        @Schema(description = "bookingId đã được chốt đầu tiên đang block segment này, nếu có", nullable = true)
        UUID blockingBookingId,
        @Schema(description = "serviceId của booking đã được chốt đang block segment này, nếu có", nullable = true)
        UUID blockingServiceId,
        @Schema(description = "Tiêu đề service của booking đã được chốt đang block segment này, nếu có", nullable = true)
        String blockingServiceTitle,
        @Schema(description = "true nếu booking đã được chốt đang block thuộc cùng service mà FE đang query")
        boolean blockedBySameService,
        @Schema(description = "true nếu booking đã được chốt đang block thuộc service khác service mà FE đang query")
        boolean blockedByDifferentService,
        @Schema(description = "Note rõ nghĩa cho FE: segment đã bị đặt bởi cùng service hay service khác", nullable = true)
        String bookingConflictNote
) {
}
