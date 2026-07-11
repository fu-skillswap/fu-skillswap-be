package com.fptu.exe.skillswap.modules.matching.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Trạng thái activation hiện tại của questionnaire")
public record AdminQuestionnaireActivationResponse(
        UUID activationId,
        UUID versionId,
        Integer versionNumber,
        LocalDateTime activatedAt,
        LocalDateTime deactivatedAt
) {
}
