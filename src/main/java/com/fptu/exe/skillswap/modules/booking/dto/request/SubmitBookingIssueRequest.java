package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload để participant báo vấn đề sau buổi mentoring")
public record SubmitBookingIssueRequest(
        @Schema(example = "NO_SHOW_OR_QUALITY_OR_OTHER")
        @NotNull(message = "issueType là bắt buộc")
        BookingIssueType issueType,

        @Schema(example = "Mentor không tham gia đúng giờ hẹn và không báo trước.")
        @NotBlank(message = "Mô tả vấn đề không được để trống")
        @Size(max = 2000, message = "Mô tả vấn đề không được vượt quá 2000 ký tự")
        String description,

        @Schema(example = "true")
        @NotNull(message = "wantsAdminReview là bắt buộc")
        Boolean wantsAdminReview
) {
}
