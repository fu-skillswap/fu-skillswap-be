package com.fptu.exe.skillswap.modules.booking.dto.response;

import com.fptu.exe.skillswap.modules.booking.domain.SessionAttendanceSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Bằng chứng check-in do hai participant tự xác nhận. Không phải kết luận tự động cho no-show hoặc hoàn tiền.")
public record SessionAttendanceResponse(
        @Schema(description = "Thời điểm mentor check-in, null khi chưa check-in", nullable = true)
        OffsetDateTime mentorCheckedInAt,
        @Schema(description = "Thời điểm mentee check-in, null khi chưa check-in", nullable = true)
        OffsetDateTime menteeCheckedInAt,
        @Schema(description = "Tóm tắt attendance hiện có", example = "BOTH")
        SessionAttendanceSummary summary,
        @Schema(description = "true nếu user đang xem đã check-in", nullable = true)
        boolean currentUserCheckedIn,
        @Schema(description = "true nếu user hiện tại có thể check-in ngay", nullable = true)
        boolean canCheckIn,
        @Schema(description = "Thời điểm CTA check-in mở", nullable = true)
        OffsetDateTime checkInOpensAt,
        @Schema(description = "Thời điểm CTA check-in đóng", nullable = true)
        OffsetDateTime checkInClosesAt
) {
}
