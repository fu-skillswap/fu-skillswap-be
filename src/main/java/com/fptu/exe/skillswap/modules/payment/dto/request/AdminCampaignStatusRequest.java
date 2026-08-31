package com.fptu.exe.skillswap.modules.payment.dto.request;

import com.fptu.exe.skillswap.modules.payment.domain.CampaignStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request thay đổi trạng thái campaign cho admin.")
public record AdminCampaignStatusRequest(
        @Schema(description = "Trạng thái mới của campaign", example = "ACTIVE")
        @NotNull(message = "Trạng thái không được để trống")
        CampaignStatus status
) {
}
