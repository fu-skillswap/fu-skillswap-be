package com.fptu.exe.skillswap.modules.course.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu cập nhật tài liệu khóa học")
public record UpdateCourseMaterialRequest(
        @Schema(description = "Tiêu đề tài liệu", example = "Bài 1: Giới thiệu Spring Boot")
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "Cho phép xem trước hay không", example = "false")
        @NotNull Boolean previewable,

        @Schema(description = "Công bố cho học viên hay không", example = "true")
        @NotNull Boolean published,

        @Schema(description = "Version hiện tại của material để chống xung đột", example = "1")
        @NotNull Long expectedVersion,

        @Schema(description = "Link YouTube mới nếu muốn đổi video (tùy chọn)", example = "https://www.youtube.com/watch?v=dQw4w9WgXcQ", nullable = true)
        @Size(max = 500) String youtubeUrl,

        @Schema(description = "Thời lượng video tính bằng giây (tùy chọn)", example = "600", nullable = true)
        @PositiveOrZero Integer durationSeconds
) {
}
