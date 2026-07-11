package com.fptu.exe.skillswap.modules.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Payload webhook chuẩn do PayOS gửi về")
public record PaymentWebhookRequest(
        @Schema(description = "Mã phản hồi của webhook PayOS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "code webhook không được để trống")
        String code,

        @Schema(description = "Mô tả phản hồi của webhook PayOS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "desc webhook không được để trống")
        String desc,

        @Schema(description = "Cờ thành công do PayOS gửi", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "success webhook không được để trống")
        Boolean success,

        @Schema(description = "Dữ liệu giao dịch do PayOS gửi", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "data webhook không được để trống")
        @Valid
        PaymentWebhookDataRequest data,

        @Schema(description = "Chữ ký xác thực webhook PayOS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "signature webhook không được để trống")
        String signature
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentWebhookDataRequest(
            @Schema(description = "Mã orderCode của merchant tại PayOS", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "data.orderCode không được để trống")
            Long orderCode,

            @Schema(description = "Số tiền PayOS nhận được", nullable = true)
            Long amount,

            @Schema(description = "Mô tả thanh toán", nullable = true)
            String description,

            @Schema(description = "Tài khoản nhận tiền", nullable = true)
            String accountNumber,

            @Schema(description = "Mã tham chiếu giao dịch ngân hàng", nullable = true)
            String reference,

            @Schema(description = "Thời điểm giao dịch ngân hàng", nullable = true)
            String transactionDateTime,

            @Schema(description = "Đơn vị tiền tệ", nullable = true)
            String currency,

            @Schema(description = "Mã payment link do PayOS sinh ra", nullable = true)
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
