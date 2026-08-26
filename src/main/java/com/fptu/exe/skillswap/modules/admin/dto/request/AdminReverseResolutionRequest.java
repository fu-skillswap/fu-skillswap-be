package com.fptu.exe.skillswap.modules.admin.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Dữ liệu admin đảo ngược quyết định dispute trước đó")
public record AdminReverseResolutionRequest(
        @Schema(description = "Lý do chuẩn cho reversal", example = "OTHER")
        @NotNull(message = "reasonCode là bắt buộc")
        AdminBookingIssueResolutionReasonCode reasonCode,

        @Schema(description = "Ghi chú lý do đảo ngược quyết định; bắt buộc cho mọi reversal",
                example = "Phát hiện thêm bằng chứng mới cho thấy quyết định trước chưa đúng.")
        @NotBlank(message = "adminNote là bắt buộc cho reversal")
        String adminNote
) {
}
