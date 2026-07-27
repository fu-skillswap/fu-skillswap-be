package com.fptu.exe.skillswap.modules.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Optional coupon input for a read-only checkout estimate")
public record PaymentCheckoutPreviewRequest(
        @Schema(nullable = true) String couponCode
) {
}
