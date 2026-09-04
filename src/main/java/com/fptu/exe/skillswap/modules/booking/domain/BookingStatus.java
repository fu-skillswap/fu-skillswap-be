package com.fptu.exe.skillswap.modules.booking.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Trạng thái booking legacy vẫn được trả để tương thích. FE mới ưu tiên bookingStatus. PENDING: chờ mentor; ACCEPTED_AWAITING_PAYMENT: đã được chấp nhận, chờ thanh toán; PAID: đã trả tiền; REJECTED/EXPIRED/CANCELLED_BY_MENTEE/CANCELLED_BY_MENTOR: kết thúc do từ chối hoặc hết hạn/hủy; AWAITING_MENTOR_COMPLETION/AWAITING_MENTEE_CONFIRMATION: đang chờ xác nhận sau buổi học; COMPLETED: hoàn tất; UNDER_REVIEW: đang xử lý issue.")
public enum BookingStatus {
    PENDING,
    ACCEPTED_AWAITING_PAYMENT,
    PAID,
    REJECTED,
    EXPIRED,
    CANCELLED_BY_MENTEE,
    CANCELLED_BY_MENTOR,
    AWAITING_MENTOR_COMPLETION,
    AWAITING_MENTEE_CONFIRMATION,
    COMPLETED,
    UNDER_REVIEW
}
