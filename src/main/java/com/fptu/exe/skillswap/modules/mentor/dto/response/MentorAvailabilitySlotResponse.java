package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilitySlotServiceBasicResponse;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Parent availability slot đang hiển thị cho mentee trên discovery. Exact queue và khả năng đặt lịch được quyết định ở candidate segment level.")
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
        @Schema(description = "Tổng số booking request PENDING hiện có trên các candidate segment thuộc slot", example = "2")
        Integer pendingRequestCount,
        @Schema(description = "Giới hạn PENDING tối đa trên từng exact candidate segment", example = "3")
        Integer maxPendingRequests,
        @Schema(description = "Số quota còn lại tốt nhất trên candidate segments hiện còn hiển thị của slot", example = "1")
        Integer remainingRequestSlots,
        @Schema(description = "Danh sách service cơ bản đang được gắn vào slot để mentee chọn trước khi lấy candidate")
        List<AvailabilitySlotServiceBasicResponse> services
) {
}
