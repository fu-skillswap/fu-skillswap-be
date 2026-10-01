package com.fptu.exe.skillswap.modules.identity.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AppPlatformConfigResponse(
        @Schema(description = "Nền tảng client", example = "android")
        String platform,

        @Schema(description = "Cho phép nạp tiền trực tiếp trong app", example = "false")
        boolean topupEnabled,

        @Schema(description = "Cho phép thanh toán checkout trực tiếp qua cổng thanh toán bên thứ ba", example = "false")
        boolean directPaymentEnabled,

        @Schema(description = "Ứng dụng hoạt động theo mô hình chỉ tiêu thụ (Consumption-Only) tuân thủ Google Play", example = "true")
        boolean consumptionOnly,

        @Schema(description = "Thông báo giải thích hiển thị cho người dùng khi cần", example = "Phiên bản ứng dụng di động chỉ hỗ trợ sử dụng số dư tài khoản có sẵn.")
        String noticeMessage
) {
}
