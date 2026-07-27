package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "Yêu cầu bật hoặc tắt dịch vụ mentoring")
public record MentorServiceActiveRequest(
        @NotNull(message = "Trạng thái active không được để trống")
        Boolean isActive,
        @NotNull(message = "expectedVersion là bắt buộc")
        @PositiveOrZero(message = "expectedVersion không hợp lệ")
        Integer expectedVersion,
        Boolean rejectPendingBookings,
        String pendingRejectionToken
) {
}
