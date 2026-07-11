package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Một môn học/kết quả học tập mentor muốn dùng làm tín hiệu matching")
public record MentorSubjectResultRequest(
        @Schema(example = "PRJ301")
        @NotBlank(message = "Mã môn không được để trống")
        @Size(max = 80, message = "Mã môn không được quá 80 ký tự")
        String subjectCode,

        @Schema(example = "Java Web Application Development")
        @Size(max = 200, message = "Tên môn không được quá 200 ký tự")
        String subjectName,

        @Schema(example = "8.6")
        @NotNull(message = "Điểm môn không được để trống")
        @DecimalMin(value = "0.0", message = "Điểm môn không được nhỏ hơn 0")
        @DecimalMax(value = "10.0", message = "Điểm môn không được lớn hơn 10")
        BigDecimal scoreValue
) {
}
