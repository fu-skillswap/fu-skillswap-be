package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fptu.exe.skillswap.shared.time.FlexibleInstantDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Payload để mentee tạo booking request mới")
public record CreateBookingRequest(
        @Schema(description = "slotId lấy từ API GET /api/mentors/{mentorUserId}/availability-slots", example = "019f2234-aaaa-bbbb-cccc-1234567890ab")
        @NotNull(message = "slotId là bắt buộc")
        UUID slotId,

        @Schema(description = "serviceId phải thuộc danh sách services được gắn vào slot đã chọn.", example = "019f3234-aaaa-bbbb-cccc-1234567890ab")
        @NotNull(message = "serviceId là bắt buộc")
        UUID serviceId,

        @Schema(description = "Thời gian bắt đầu (chấp nhận cả ISO-8601 UTC '2026-08-30T11:16:00Z' hoặc giờ VN '2026-08-30T18:16:00')", example = "2026-08-30T18:16:00")
        @NotNull(message = "startAt là bắt buộc")
        @JsonDeserialize(using = FlexibleInstantDeserializer.class)
        Instant startAt,

        @Schema(description = "Tiêu đề mục tiêu học tập ngắn gọn để mentor nhìn nhanh", example = "Review lộ trình học Spring Boot và chuẩn bị phỏng vấn intern")
        @NotBlank(message = "learningGoalTitle không được để trống")
        @Size(max = 200, message = "learningGoalTitle không được vượt quá 200 ký tự")
        String learningGoalTitle,

        @Schema(description = "Mô tả chi tiết vấn đề mentee muốn mentor hỗ trợ", nullable = true, example = "Em muốn được góp ý CV backend, định hướng học PRJ301 và cách làm project REST API với PostgreSQL.")
        @Size(max = 2000, message = "learningGoalDescription không được vượt quá 2000 ký tự")
        String learningGoalDescription
) {
}
