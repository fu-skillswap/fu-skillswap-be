package com.fptu.exe.skillswap.modules.course.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu tạo bài học video bằng link YouTube")
public record CreateYouTubeVideoMaterialRequest(
        @Schema(description = "Tiêu đề bài học video", example = "Bài 1: Giới thiệu Spring Boot", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Tiêu đề không được để trống")
        @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự")
        String title,

        @Schema(description = "Link video YouTube (dạng watch link, short link youtu.be, embed hoặc shorts)", example = "https://www.youtube.com/watch?v=dQw4w9WgXcQ", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Link YouTube không được để trống")
        @Size(max = 500, message = "URL tối đa 500 ký tự")
        String youtubeUrl,

        @Schema(description = "Thời lượng video tính bằng giây (tùy chọn)", example = "600", nullable = true)
        @PositiveOrZero(message = "Thời lượng video phải >= 0")
        Integer durationSeconds,

        @Schema(description = "Vị trí hiển thị trong chương", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Thứ tự sắp xếp không được để trống")
        Integer sortOrder,

        @Schema(description = "Cho phép học viên xem thử trước khi mua khóa học hay không", example = "false", nullable = true)
        Boolean previewable,

        @Schema(description = "Công bố bài học cho học viên xem hay chưa", example = "true", nullable = true)
        Boolean published
) {
}
