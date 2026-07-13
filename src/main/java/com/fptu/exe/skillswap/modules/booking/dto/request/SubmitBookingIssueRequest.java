package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload để participant báo vấn đề sau buổi mentoring")
public record SubmitBookingIssueRequest(
        @Schema(example = "MENTOR_NO_SHOW")
        @NotNull(message = "issueType là bắt buộc")
        BookingIssueType issueType,

        @Schema(example = "Mentor không tham gia đúng giờ hẹn và không báo trước.")
        @NotBlank(message = "Mô tả vấn đề không được để trống")
        @Size(max = 2000, message = "Mô tả vấn đề không được vượt quá 2000 ký tự")
        String description
) {
}
