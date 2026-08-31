package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction;
import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Dữ liệu admin resolve booking issue")
public record AdminResolveBookingIssueRequest(
        @Schema(description = "Cách admin đóng issue", example = "PARTIAL_SETTLEMENT")
        @NotNull(message = "action là bắt buộc")
        AdminBookingIssueResolutionAction action,

        @Schema(description = "Lý do chuẩn để audit/reporting", example = "QUALITY_PARTIAL_COMPENSATION")
        @NotNull(message = "reasonCode là bắt buộc")
        AdminBookingIssueResolutionReasonCode reasonCode,

        @Schema(description = "Ghi chú xử lý. Bắt buộc khi PARTIAL_SETTLEMENT hoặc reasonCode OTHER.", example = "Buổi học diễn ra nhưng nội dung chỉ đáp ứng một phần mục tiêu.")
        String adminNote,

        @Schema(description = "Tỷ lệ hoàn cho mentee theo basis points. Chỉ dùng cho PARTIAL_SETTLEMENT.", example = "5000")
        @Min(value = 0, message = "menteeBps không được âm") @Max(value = 10000, message = "menteeBps không vượt quá 10000")
        Integer menteeBps,

        @Schema(description = "Tỷ lệ trả mentor theo basis points. Chỉ dùng cho PARTIAL_SETTLEMENT.", example = "3500")
        @Min(value = 0, message = "mentorBps không được âm") @Max(value = 10000, message = "mentorBps không vượt quá 10000")
        Integer mentorBps,

        @Schema(description = "Tỷ lệ nền tảng giữ theo basis points. Chỉ dùng cho PARTIAL_SETTLEMENT. Tổng ba tỷ lệ phải bằng 10000.", example = "1500")
        @Min(value = 0, message = "platformBps không được âm") @Max(value = 10000, message = "platformBps không vượt quá 10000")
        Integer platformBps
) {

    /** Compatibility constructor for existing internal callers of the former two-field contract. */
    public AdminResolveBookingIssueRequest(AdminBookingIssueResolutionAction action, String adminNote) {
        this(action, defaultReason(action), adminNote, null, null, null);
    }

    private static AdminBookingIssueResolutionReasonCode defaultReason(AdminBookingIssueResolutionAction action) {
        if (action == AdminBookingIssueResolutionAction.CONFIRM_MENTOR_NO_SHOW_REFUND
                || action == AdminBookingIssueResolutionAction.CONFIRM_MENTEE_NO_SHOW_RELEASE) {
            return AdminBookingIssueResolutionReasonCode.NO_SHOW_CONFIRMED;
        }
        return AdminBookingIssueResolutionReasonCode.SESSION_CONFIRMED;
    }
}
