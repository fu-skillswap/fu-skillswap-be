package com.fptu.exe.skillswap.modules.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourseChapterRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        Boolean published
) {
}
