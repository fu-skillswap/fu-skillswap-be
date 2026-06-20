package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Slot availability đang hiển thị cho mentee trên discovery")
public record MentorAvailabilitySlotResponse(
        @Schema(description = "ID của slot", example = "019f5234-aaaa-bbbb-cccc-1234567890ab")
        UUID slotId,
        @Schema(description = "Thời gian bắt đầu slot")
        LocalDateTime startTime,
        @Schema(description = "Thời gian kết thúc slot")
        LocalDateTime endTime,
        @Schema(description = "Timezone hệ thống dùng để hiển thị và tính business rule", example = "Asia/Ho_Chi_Minh")
        String timezone,
        @Schema(description = "Thời lượng slot tính theo phút", example = "60")
        Integer durationMinutes,
        @Schema(description = "Hình thức mentoring của slot", example = "ONLINE")
        TeachingMode teachingMode,
        @Schema(description = "Slot này được sinh từ recurring rule hay không")
        boolean recurring,
        @Schema(description = "Số booking request PENDING hiện tại trong hàng đợi của slot", example = "2")
        Integer pendingRequestCount,
        @Schema(description = "Số request PENDING tối đa mà slot có thể nhận cùng lúc", example = "3")
        Integer maxPendingRequests,
        @Schema(description = "Số suất request còn có thể nhận thêm trước khi slot bị ẩn khỏi availability", example = "1")
        Integer remainingRequestSlots
) {
}
