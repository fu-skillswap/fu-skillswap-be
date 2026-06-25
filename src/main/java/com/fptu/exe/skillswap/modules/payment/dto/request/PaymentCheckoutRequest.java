package com.fptu.exe.skillswap.modules.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request checkout payment cho một booking đã sẵn sàng thanh toán")
public record PaymentCheckoutRequest(
        @Schema(description = "Booking cần thanh toán", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "bookingId không được để trống")
        UUID bookingId,

        @Schema(description = "Mã coupon nếu người dùng muốn áp dụng", nullable = true)
        String couponCode
) {
}
