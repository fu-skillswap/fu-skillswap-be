package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload để participant xác nhận buổi mentoring đã diễn ra ổn")
public record ConfirmBookingRequest(
        @Schema(description = "Ghi chú xác nhận sau buổi học", nullable = true, example = "Buổi mentoring diễn ra đúng kế hoạch và hữu ích.")
        @Size(max = 2000, message = "confirmationNote không được vượt quá 2000 ký tự")
        String confirmationNote
) {
}
