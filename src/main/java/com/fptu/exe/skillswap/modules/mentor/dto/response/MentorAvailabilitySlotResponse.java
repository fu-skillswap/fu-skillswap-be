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
        @Schema(description = "Thời lượng parent slot tính theo phút, được suy ra từ endTime - startTime. Đây là field dẫn xuất để FE tiện hiển thị, không phải duration của service.", example = "60", deprecated = true)
        Integer durationMinutes,
        @Schema(description = "Hình thức mentoring của slot", example = "ONLINE")
        TeachingMode teachingMode,
        @Schema(description = "Tổng số booking request PENDING đã gửi vào parent slot này ở thời điểm hiện tại", example = "2")
        Integer pendingRequestCount,
        @Schema(description = "Số booking trong slot này đã được mentor chấp nhận ở trạng thái ACCEPTED", example = "1")
        Integer acceptedSlotCount,
        @Schema(description = "Legacy field cũ của phase candidate summary. FE mới không nên dùng ở parent slot.", example = "3", deprecated = true, hidden = true)
        Integer maxPendingRequests,
        @Schema(description = "Legacy field cũ của phase candidate summary. FE mới không nên dùng ở parent slot.", example = "1", deprecated = true, hidden = true)
        Integer remainingRequestSlots,
        @Schema(description = "Danh sách service cơ bản đang được gắn vào slot. FE chọn serviceId từ đây trước khi gọi API candidates.")
        List<AvailabilitySlotServiceBasicResponse> services
) {
}
