package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Payload báo vấn đề sau buổi mentoring. MENTOR_NO_SHOW chỉ mentee được gửi; MENTEE_NO_SHOW chỉ mentor được gửi; các loại khác cho phép cả hai bên.")
public record SubmitBookingIssueRequest(
        @Schema(example = "MENTOR_NO_SHOW")
        @NotNull(message = "issueType là bắt buộc")
        BookingIssueType issueType,

        @Schema(example = "Mentor không tham gia đúng giờ hẹn và không báo trước.")
        @NotBlank(message = "Mô tả vấn đề không được để trống")
        @Size(max = 2000, message = "Mô tả vấn đề không được vượt quá 2000 ký tự")
        String description,

        @Schema(description = "1 đến 5 evidenceId đã confirm. Chỉ reporter bắt buộc gửi minh chứng.")
        @jakarta.validation.constraints.NotEmpty(message = "Cần gửi ít nhất một file minh chứng")
        @Size(max = 5, message = "Tối đa 5 file minh chứng cho một issue")
        List<@NotNull UUID> evidenceIds
) {
}
