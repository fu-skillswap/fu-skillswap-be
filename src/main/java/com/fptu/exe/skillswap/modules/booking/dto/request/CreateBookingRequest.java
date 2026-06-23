package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Payload để mentee tạo booking request mới")
public record CreateBookingRequest(
        @Schema(description = "userId của mentor được chọn trên discovery", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
        @NotNull(message = "mentorUserId là bắt buộc")
        UUID mentorUserId,

        @Schema(description = "slotId lấy từ API GET /api/mentors/{mentorUserId}/availability", example = "019f2234-aaaa-bbbb-cccc-1234567890ab")
        @NotNull(message = "availabilitySlotId là bắt buộc")
        UUID availabilitySlotId,

        @Schema(description = "serviceId bắt buộc phải có cho booking mới.", example = "019f3234-aaaa-bbbb-cccc-1234567890ab")
        @NotNull(message = "serviceId là bắt buộc")
        UUID serviceId,

        @Schema(description = "Tiêu đề mục tiêu học tập ngắn gọn để mentor nhìn nhanh", example = "Review lộ trình học Spring Boot và chuẩn bị phỏng vấn intern")
        @NotBlank(message = "learningGoalTitle không được để trống")
        @Size(max = 200, message = "learningGoalTitle không được vượt quá 200 ký tự")
        String learningGoalTitle,

        @Schema(description = "Mô tả chi tiết vấn đề mentee muốn mentor hỗ trợ", nullable = true, example = "Em muốn được góp ý CV backend, định hướng học PRJ301 và cách làm project REST API với PostgreSQL.")
        @Size(max = 2000, message = "learningGoalDescription không được vượt quá 2000 ký tự")
        String learningGoalDescription
) {
}
