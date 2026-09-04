package com.fptu.exe.skillswap.modules.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mã coupon tùy chọn cho bản ước tính chỉ đọc. Không tạo payment order và không giữ credit/coupon.")
public record PaymentCheckoutPreviewRequest(
        @Schema(description = "Mã coupon người dùng muốn xem thử; để null nếu không áp dụng.", example = "WELCOME10", nullable = true) String couponCode
) {
}
