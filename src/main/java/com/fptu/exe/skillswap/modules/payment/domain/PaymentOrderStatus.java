package com.fptu.exe.skillswap.modules.payment.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trạng thái payment order hiển thị cho FE. PENDING/AWAITING_PROVIDER_PAYMENT: còn chờ thanh toán; PARTIALLY_COVERED_BY_CREDIT: một phần đã dùng credit; PAID: hoàn tất; FAILED: provider thất bại, chỉ tạo checkout mới khi retryable=true; CANCELLED: đã hủy; EXPIRED: link hết hạn và có thể bắt đầu lại nếu booking còn cho phép.")
public enum PaymentOrderStatus {
    PENDING,
    PARTIALLY_COVERED_BY_CREDIT,
    AWAITING_PROVIDER_PAYMENT,
    PAID,
    FAILED,
    CANCELLED,
    EXPIRED
}
