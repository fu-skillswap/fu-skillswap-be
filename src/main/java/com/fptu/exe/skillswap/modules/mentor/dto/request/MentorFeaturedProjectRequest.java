package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dự án tiêu biểu của mentor")
public record MentorFeaturedProjectRequest(
        @Schema(example = "SWP391 Booking Platform")
        @NotBlank(message = "Tên dự án không được để trống")
        @Size(max = 200, message = "Tên dự án không được quá 200 ký tự")
        String title,

        @Schema(example = "Vai trò, công nghệ, hoặc điểm nổi bật của dự án")
        @Size(max = 2000, message = "Nội dung dự án không được quá 2000 ký tự")
        String content,

        @Schema(example = "Mô tả ngắn vấn đề, cách làm và kết quả của dự án")
        @Size(max = 2000, message = "Mô tả dự án không được quá 2000 ký tự")
        String projectDescription,

        @Schema(example = "https://demo.example.com")
        String liveDemoUrl
) {
}
