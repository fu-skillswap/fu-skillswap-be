package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Payload để mentee tạo booking request mới")
public record CreateBookingRequest(
        @Schema(description = "slotId lấy từ API GET /api/mentors/{mentorUserId}/availability-slots", example = "019f2234-aaaa-bbbb-cccc-1234567890ab")
        @NotNull(message = "availabilitySlotId là bắt buộc")
        UUID availabilitySlotId,

        @Schema(description = "serviceId phải thuộc danh sách services được gắn vào slot đã chọn.", example = "019f3234-aaaa-bbbb-cccc-1234567890ab")
        @NotNull(message = "serviceId là bắt buộc")
        UUID serviceId,

        @Schema(description = "Thời gian bắt đầu mentee thực sự chọn trong slot, phải khớp exact candidate hợp lệ từ API candidates", example = "2026-06-30T19:00:00")
        @NotNull(message = "selectedStartTime là bắt buộc")
        LocalDateTime selectedStartTime,

        @Schema(description = "Thời gian kết thúc mentee thực sự chọn trong slot, phải khớp exact candidate hợp lệ từ API candidates", example = "2026-06-30T19:30:00")
        @NotNull(message = "selectedEndTime là bắt buộc")
        LocalDateTime selectedEndTime,

        @Schema(description = "Tiêu đề mục tiêu học tập ngắn gọn để mentor nhìn nhanh", example = "Review lộ trình học Spring Boot và chuẩn bị phỏng vấn intern")
        @NotBlank(message = "learningGoalTitle không được để trống")
        @Size(max = 200, message = "learningGoalTitle không được vượt quá 200 ký tự")
        String learningGoalTitle,

        @Schema(description = "Mô tả chi tiết vấn đề mentee muốn mentor hỗ trợ", nullable = true, example = "Em muốn được góp ý CV backend, định hướng học PRJ301 và cách làm project REST API với PostgreSQL.")
        @Size(max = 2000, message = "learningGoalDescription không được vượt quá 2000 ký tự")
        String learningGoalDescription
) {
    @AssertTrue(message = "selectedEndTime phải sau selectedStartTime")
    public boolean isSelectedRangeValid() {
        return selectedStartTime != null
                && selectedEndTime != null
                && selectedEndTime.isAfter(selectedStartTime);
    }
}
