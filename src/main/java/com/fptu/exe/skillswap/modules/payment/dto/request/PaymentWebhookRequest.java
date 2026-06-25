package com.fptu.exe.skillswap.modules.payment.dto.request;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(description = "Webhook payload tối giản để mark payment order đã xử lý")
public record PaymentWebhookRequest(
        @Schema(description = "Mã order phía provider", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "providerOrderCode không được để trống")
        String providerOrderCode,

        @Schema(description = "Mã giao dịch phía provider", nullable = true)
        String providerTransactionId,

        @Schema(description = "Trạng thái cuối từ cổng thanh toán", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "status không được để trống")
        PaymentOrderStatus status,

        @Schema(description = "Thời điểm thanh toán", nullable = true)
        LocalDateTime paidAt,

        @Schema(description = "Thông điệp lỗi nếu thất bại", nullable = true)
        String failureReason
) {
}
