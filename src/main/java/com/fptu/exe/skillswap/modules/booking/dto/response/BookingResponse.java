package com.fptu.exe.skillswap.modules.booking.dto.response;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Thông tin booking mentoring dùng chung cho mentee, mentor và admin")
public record BookingResponse(
        @Schema(description = "ID của booking", example = "019f4234-aaaa-bbbb-cccc-1234567890ab")
        UUID bookingId,
        @Schema(description = "Hiện đang map theo sessionId nội bộ nếu đã có session, chưa có thì có thể null", nullable = true)
        UUID sessionId,
        @Schema(description = "Trạng thái session/buổi học hiện tại theo model hiện có", nullable = true, example = "COMPLETED")
        BookingStatus sessionStatus,
        @Schema(description = "userId của mentor")
        UUID mentorUserId,
        @Schema(description = "Tên hiển thị của mentor")
        String mentorDisplayName,
        @Schema(description = "Avatar của mentor", nullable = true)
        String mentorAvatarUrl,
        @Schema(description = "userId của mentee")
        UUID menteeUserId,
        @Schema(description = "Tên hiển thị của mentee")
        String menteeDisplayName,
        @Schema(description = "Avatar của mentee", nullable = true)
        String menteeAvatarUrl,
        @Schema(description = "slotId đã dùng để tạo booking")
        UUID slotId,
        @Schema(description = "serviceId được gắn với booking nếu mentee chọn service cụ thể", nullable = true)
        UUID serviceId,
        @Schema(description = "Tiêu đề service nếu có", nullable = true)
        String serviceTitle,
        @Schema(description = "Trạng thái booking hiện tại", example = "PENDING")
        BookingStatus status,
        @Schema(description = "Tiêu đề mục tiêu học tập")
        String learningGoalTitle,
        @Schema(description = "Mô tả chi tiết mục tiêu học tập", nullable = true)
        String learningGoalDescription,
        @Schema(description = "Ghi chú phản hồi của mentor khi accept/reject", nullable = true)
        String mentorResponseNote,
        @Schema(description = "Lý do từ chối booking", nullable = true)
        String rejectReason,
        @Schema(description = "Lý do hủy booking", nullable = true)
        String cancelReason,
        @Schema(description = "Nền tảng meeting mentor cấu hình", nullable = true, example = "GOOGLE_MEET")
        MeetingPlatform meetingPlatform,
        @Schema(description = "Đường dẫn meeting online", nullable = true)
        String meetingLink,
        @Schema(description = "Địa điểm gặp nếu mentoring offline hoặc ghi chú vị trí", nullable = true)
        String location,
        @Schema(description = "Thời gian bắt đầu đã request theo slot")
        LocalDateTime requestedStartTime,
        @Schema(description = "Thời gian kết thúc đã request theo slot")
        LocalDateTime requestedEndTime,
        @Schema(description = "Thời gian bắt đầu thực tế nếu có", nullable = true)
        LocalDateTime actualStartTime,
        @Schema(description = "Thời gian kết thúc thực tế nếu có", nullable = true)
        LocalDateTime actualEndTime,
        @Schema(description = "Thời điểm mentor accept booking", nullable = true)
        LocalDateTime acceptedAt,
        @Schema(description = "Thời điểm mentor reject booking", nullable = true)
        LocalDateTime rejectedAt,
        @Schema(description = "Thời điểm booking bị hủy", nullable = true)
        LocalDateTime cancelledAt,
        @Schema(description = "Thời điểm booking được đánh dấu hoàn thành", nullable = true)
        LocalDateTime completedAt,
        @Schema(description = "Ghi chú của mentor sau buổi học", nullable = true)
        String mentorNote,
        @Schema(description = "Ghi chú của mentee sau buổi học", nullable = true)
        String menteeNote,
        @Schema(description = "Thời điểm tạo booking")
        LocalDateTime createdAt,
        @Schema(description = "Thời điểm cập nhật booking gần nhất")
        LocalDateTime updatedAt
) {
}
