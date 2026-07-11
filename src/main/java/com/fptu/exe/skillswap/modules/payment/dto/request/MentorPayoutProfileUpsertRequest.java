package com.fptu.exe.skillswap.modules.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request tạo hoặc cập nhật payout profile của mentor")
public record MentorPayoutProfileUpsertRequest(
        @NotBlank(message = "accountHolderName không được để trống")
        @Schema(description = "Tên chủ tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        String accountHolderName,

        @Schema(description = "Mã ngân hàng nếu FE có sẵn danh mục", nullable = true)
        String bankCode,

        @NotBlank(message = "bankName không được để trống")
        @Schema(description = "Tên ngân hàng", requiredMode = Schema.RequiredMode.REQUIRED)
        String bankName,

        @NotBlank(message = "accountNumber không được để trống")
        @Schema(description = "Số tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        String accountNumber,

        @Schema(description = "Đặt làm payout profile mặc định", nullable = true)
        Boolean isDefault,

        @Schema(description = "Profile còn hoạt động hay không", nullable = true)
        Boolean isActive
) {
}
