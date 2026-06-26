package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Dữ liệu admin resolve booking issue")
public record AdminResolveBookingIssueRequest(
        @Schema(description = "Cách admin đóng issue", example = "COMPLETE")
        @NotNull(message = "action là bắt buộc")
        AdminBookingIssueResolutionAction action,

        @Schema(description = "Ghi chú xử lý của admin", example = "Đã xác minh dispute và chốt buổi học hoàn tất.")
        @NotBlank(message = "adminNote là bắt buộc")
        String adminNote
) {
}
