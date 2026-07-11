package com.fptu.exe.skillswap.modules.matching.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Builder
@Schema(description = "Trạng thái hồ sơ nhu cầu mentoring của user hiện tại")
public record MentoringMatchProfileResponse(
        boolean exists,
        boolean currentActivationCompleted,
        LocalDateTime latestAnsweredAt,
        UUID activeActivationId,
        UUID activeVersionId,
        Integer activeVersionNumber,
        Integer foundationNeedLevel,
        Integer outputReviewNeedLevel,
        Integer directionNeedLevel,
        String mentorFitCode,
        String durationPreferenceCode,
        Map<String, String> latestAnswerCodes
) {
}
