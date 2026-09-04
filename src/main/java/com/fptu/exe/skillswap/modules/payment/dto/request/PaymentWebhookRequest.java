package com.fptu.exe.skillswap.modules.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Internal/System - không dùng cho FE. Payload webhook chuẩn do PayOS gửi về; backend xác thực signature trước khi xử lý.")
public record PaymentWebhookRequest(
        @Schema(description = "Mã phản hồi của webhook PayOS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "code webhook không được để trống")
        String code,

            @Schema(description = "Internal/System - mô tả phản hồi của webhook PayOS.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "desc webhook không được để trống")
        String desc,

            @Schema(description = "Internal/System - cờ thành công do PayOS gửi.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "success webhook không được để trống")
        Boolean success,

            @Schema(description = "Internal/System - dữ liệu giao dịch do PayOS gửi.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "data webhook không được để trống")
        @Valid
        PaymentWebhookDataRequest data,

            @Schema(description = "Internal/System - chữ ký xác thực webhook PayOS; không phải token đăng nhập của FE.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "signature webhook không được để trống")
        String signature
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentWebhookDataRequest(
            @Schema(description = "Internal/System - mã orderCode của merchant tại PayOS.", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "data.orderCode không được để trống")
            Long orderCode,

            @Schema(description = "Internal/System - số tiền PayOS nhận được.", nullable = true)
            Long amount,

            @Schema(description = "Internal/System - mô tả thanh toán do provider gửi.", nullable = true)
            String description,

            @Schema(description = "Internal/System - tài khoản nhận tiền do provider gửi.", nullable = true)
            String accountNumber,

            @Schema(description = "Internal/System - mã tham chiếu giao dịch ngân hàng do provider gửi.", nullable = true)
            String reference,

            @Schema(description = "Internal/System - thời điểm giao dịch ngân hàng do provider gửi.", nullable = true)
            String transactionDateTime,

            @Schema(description = "Internal/System - đơn vị tiền tệ do provider gửi.", nullable = true)
            String currency,

            @Schema(description = "Internal/System - mã payment link do PayOS sinh ra.", nullable = true)
            String paymentLinkId,

            @Schema(description = "Mã trạng thái giao dịch trong data", nullable = true)
            String code,

            @Schema(description = "Mô tả trạng thái giao dịch trong data", nullable = true)
            String desc,

            @Schema(nullable = true)
            String counterAccountBankId,

            @Schema(nullable = true)
            String counterAccountBankName,

            @Schema(nullable = true)
            String counterAccountName,

            @Schema(nullable = true)
            String counterAccountNumber,

            @Schema(nullable = true)
            String virtualAccountName,

            @Schema(nullable = true)
            String virtualAccountNumber
    ) {
    }
}
