package com.fptu.exe.skillswap.modules.booking.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trạng thái thanh toán public của booking. NOT_REQUIRED: booking miễn phí; PENDING: đang chờ thanh toán; PAID: đã thanh toán; FAILED: thanh toán thất bại và có thể thử lại nếu retryable=true; EXPIRED: hết thời hạn thanh toán; CANCELLED: đã hủy; REFUNDED: đã hoàn tiền. FE không tự đổi trạng thái, chỉ tải lại trạng thái từ API.")
public enum BookingPaymentStatus {
    NOT_REQUIRED,
    PENDING,
    PAID,
    FAILED,
    EXPIRED,
    CANCELLED,
    REFUNDED
}
