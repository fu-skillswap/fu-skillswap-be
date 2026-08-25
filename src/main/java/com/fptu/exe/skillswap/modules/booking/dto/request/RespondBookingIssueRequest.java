package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Phản hồi một lần của counterparty cho booking issue")
public record RespondBookingIssueRequest(
        @NotBlank(message = "Nội dung phản hồi không được để trống")
        @Size(max = 2000, message = "Nội dung phản hồi không được vượt quá 2000 ký tự")
        String responseNote,

        @Schema(description = "Danh sách 0 đến 5 evidenceId đã confirm của counterparty", nullable = true)
        @Size(max = 5, message = "Tối đa 5 file minh chứng cho một phản hồi")
        List<@jakarta.validation.constraints.NotNull UUID> evidenceIds
) {
}
