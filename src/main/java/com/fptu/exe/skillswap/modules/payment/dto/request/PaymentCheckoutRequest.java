package com.fptu.exe.skillswap.modules.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Yêu cầu tạo phiên thanh toán cho booking đã sẵn sàng thanh toán. Backend tự xác định người thanh toán từ tài khoản đăng nhập và tự tính lại số tiền.")
public record PaymentCheckoutRequest(
        @Schema(description = "ID booking người dùng muốn thanh toán.", example = "019f1234-aaaa-bbbb-cccc-1234567890ab", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "bookingId không được để trống")
        UUID bookingId,

        @Schema(description = "Mã coupon người dùng muốn áp dụng; để null nếu không dùng coupon. Backend kiểm tra lại hiệu lực và điều kiện sử dụng.", example = "WELCOME10", nullable = true)
        String couponCode
) {
}
