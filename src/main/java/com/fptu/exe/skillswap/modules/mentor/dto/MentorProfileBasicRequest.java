package com.fptu.exe.skillswap.modules.mentor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Step 1 - Thông tin nền tảng của mentor profile")
public record MentorProfileBasicRequest(
        @Schema(example = "Backend Developer | Spring Boot Mentor", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Tiêu đề hồ sơ mentor không được để trống")
        @Size(max = 200, message = "Tiêu đề hồ sơ mentor không được quá 200 ký tự")
        String headline,

        @Schema(example = "Software Engineer", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Vị trí hiện tại không được để trống")
        @Size(max = 150, message = "Vị trí hiện tại không được quá 150 ký tự")
        String currentPosition,

        @Schema(example = "FPT Software", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Công ty hiện tại không được để trống")
        @Size(max = 150, message = "Công ty hiện tại không được quá 150 ký tự")
        String currentCompany,

        @Schema(example = "https://res.cloudinary.com/example/avatar.jpg", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Ảnh đại diện không được để trống")
        String avatarUrl,

        @Schema(example = "Mình hỗ trợ sinh viên định hướng backend và chuẩn bị phỏng vấn.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Phần giới thiệu bản thân không được để trống")
        @Size(max = 3000, message = "Phần giới thiệu bản thân không được quá 3000 ký tự")
        String bio,

        @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Trạng thái sẵn sàng mentoring không được để trống")
        Boolean isAvailable
) {
}
