package com.fptu.exe.skillswap.modules.booking.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trạng thái vòng đời booking dùng trong API public. REQUESTED: chờ mentor phản hồi; WAITING_PAYMENT: mentor đã chấp nhận và chờ thanh toán; CONFIRMED: booking đã xác nhận; REJECTED_BY_MENTOR: mentor từ chối; CANCELED_BY_MENTEE/CANCELED_BY_MENTOR: đã hủy; REQUEST_EXPIRED: hết hạn chờ mentor; PAYMENT_EXPIRED: hết hạn thanh toán; UNDER_REVIEW: đang xử lý issue; COMPLETED: đã hoàn tất. FE hiển thị theo trạng thái và chỉ thực hiện action khi cờ can* hoặc nextAction cho phép.")
public enum BookingLifecycleStatus {
    REQUESTED,
    WAITING_PAYMENT,
    CONFIRMED,
    REJECTED_BY_MENTOR,
    CANCELED_BY_MENTEE,
    CANCELED_BY_MENTOR,
    REQUEST_EXPIRED,
    PAYMENT_EXPIRED,
    UNDER_REVIEW,
    COMPLETED
}
