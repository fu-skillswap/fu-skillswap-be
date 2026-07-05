package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Schema(description = "Môn học/kết quả học tập mentor dùng cho peer matching")
public record MentorSubjectResultResponse(
        UUID id,
        String subjectCode,
        String subjectName,
        BigDecimal scoreValue,
        Integer displayOrder
) {
}
