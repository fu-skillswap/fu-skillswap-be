package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

        @Schema(description = "UTC whole-minute candidate start lấy từ API candidates. Backend tự suy ra end time từ duration của service.", example = "2026-06-30T12:00:00Z")
        @NotNull(message = "startAt là bắt buộc")
        Instant startAt,

        @Schema(description = "Tiêu đề mục tiêu học tập ngắn gọn để mentor nhìn nhanh", example = "Review lộ trình học Spring Boot và chuẩn bị phỏng vấn intern")
        @NotBlank(message = "learningGoalTitle không được để trống")
        @Size(max = 200, message = "learningGoalTitle không được vượt quá 200 ký tự")
        String learningGoalTitle,

        @Schema(description = "Mô tả chi tiết vấn đề mentee muốn mentor hỗ trợ", nullable = true, example = "Em muốn được góp ý CV backend, định hướng học PRJ301 và cách làm project REST API với PostgreSQL.")
        @Size(max = 2000, message = "learningGoalDescription không được vượt quá 2000 ký tự")
        String learningGoalDescription,

        @JsonIgnore
        java.time.LocalDateTime legacySelectedEndTime
) {
    public CreateBookingRequest(UUID slotId, UUID serviceId, Instant startAt,
                                String learningGoalTitle, String learningGoalDescription) {
        this(slotId, serviceId, startAt, learningGoalTitle, learningGoalDescription, null);
    }
    /** Java-only bridge for internal callers/tests compiled against the pre-launch DTO. */
    @Deprecated(forRemoval = true)
    public CreateBookingRequest(UUID availabilitySlotId, UUID serviceId,
                                java.time.LocalDateTime selectedStartTime,
                                java.time.LocalDateTime ignoredSelectedEndTime,
                                String learningGoalTitle, String learningGoalDescription) {
        this(availabilitySlotId, serviceId,
                selectedStartTime == null ? null : selectedStartTime.toInstant(java.time.ZoneOffset.UTC),
                learningGoalTitle, learningGoalDescription, ignoredSelectedEndTime);
    }

    @Deprecated(forRemoval = true)
    public java.time.LocalDateTime selectedStartTime() {
        return startAt == null ? null : java.time.LocalDateTime.ofInstant(startAt, java.time.ZoneOffset.UTC);
    }

    @Deprecated(forRemoval = true)
    public java.time.LocalDateTime selectedEndTime() {
        return legacySelectedEndTime != null ? legacySelectedEndTime
                : (startAt == null ? null : java.time.LocalDateTime.ofInstant(startAt.plusSeconds(3600), java.time.ZoneOffset.UTC));
    }
}
