package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload mentor từ chối booking")
public record RejectBookingRequest(
        @Schema(description = "Lý do từ chối chính thức trả về cho mentee", example = "Khung giờ này anh không còn phù hợp, em vui lòng chọn slot khác.")
        @NotBlank(message = "rejectReason không được để trống")
        @Size(max = 2000, message = "rejectReason không được vượt quá 2000 ký tự")
        String rejectReason,

        @Schema(description = "Ghi chú bổ sung của mentor", nullable = true, example = "Nếu cần review CV thì em có thể đặt lại vào tối thứ 5.")
        @Size(max = 2000, message = "mentorResponseNote không được vượt quá 2000 ký tự")
        String mentorResponseNote
) {
}
