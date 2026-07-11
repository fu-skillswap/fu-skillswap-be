package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload mentor chấp nhận booking")
public record AcceptBookingRequest(
        @Schema(description = "Ghi chú tùy chọn của mentor gửi cho mentee khi accept", nullable = true, example = "Anh đã xem mục tiêu của em, mình sẽ tập trung vào phần REST API và mock interview.")
        @Size(max = 2000, message = "mentorResponseNote không được vượt quá 2000 ký tự")
        String mentorResponseNote
) {
}
