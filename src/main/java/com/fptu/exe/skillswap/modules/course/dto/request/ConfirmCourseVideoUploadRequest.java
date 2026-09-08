package com.fptu.exe.skillswap.modules.course.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

@Schema(description = "Metadata xác nhận video R2.")
public record ConfirmCourseVideoUploadRequest(
        @Schema(description = "Thời lượng video tính bằng giây.", example = "420", requiredMode = Schema.RequiredMode.REQUIRED)
        @Positive(message = "durationSeconds phải lớn hơn 0")
        Integer durationSeconds
) {
}
