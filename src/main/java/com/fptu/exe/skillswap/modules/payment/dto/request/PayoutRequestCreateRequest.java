package com.fptu.exe.skillswap.modules.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request tạo payout cho mentor")
public record PayoutRequestCreateRequest(
        @Schema(description = "Số SCoin muốn rút", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "amountScoin không được để trống")
        @Min(value = 1, message = "amountScoin phải lớn hơn 0")
        Integer amountScoin,

        @Schema(description = "Ghi chú bổ sung", nullable = true)
        String note
) {
}
