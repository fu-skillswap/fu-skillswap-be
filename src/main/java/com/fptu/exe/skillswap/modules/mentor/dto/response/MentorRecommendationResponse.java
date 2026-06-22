package com.fptu.exe.skillswap.modules.mentor.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Builder
@Schema(description = "Kết quả gợi ý mentor (Dùng trên trang chủ Dashboard)")
public record MentorRecommendationResponse(
        @Schema(description = "Thông tin tóm tắt của mentor")
        MentorDiscoveryCardResponse mentor,
        @Schema(description = "Điểm phù hợp dựa trên thuật toán gợi ý", example = "95.5")
        BigDecimal matchScore,
        @Schema(description = "Lý do hệ thống gợi ý mentor này", example = "[\"Cùng chuyên ngành Kỹ thuật phần mềm\", \"Mentor có đánh giá cao\"]")
        List<String> matchReasons
) {
}
