package com.fptu.exe.skillswap.modules.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Bản ước tính chỉ đọc cho màn hình xác nhận thanh toán. Khi checkout thật, backend sẽ tính lại toàn bộ số tiền.")
public record PaymentCheckoutPreviewResponse(
        @Schema(description = "ID booking được xem trước thanh toán.") UUID bookingId,
        @Schema(description = "Giá dịch vụ cuối cùng theo Scoin trước khi trừ các khoản giảm/credit.") Integer priceScoin,
        @Schema(description = "Giá trước khi giảm bởi coupon.") Integer priceBeforeDiscountScoin,
        @Schema(description = "Số Scoin giảm bởi coupon.") Integer couponDiscountScoin,
        @Schema(description = "Số credit từ campaign được ước tính áp dụng.") Integer campaignCreditAppliedScoin,
        @Schema(description = "Số credit của người dùng được ước tính áp dụng.") Integer userCreditAppliedScoin,
        @Schema(description = "Số Scoin ước tính còn phải thanh toán.") Integer estimatedFinalPayableScoin,
        @Schema(description = "Thời hạn thanh toán kèm offset +07:00", example = "2026-08-25T10:00:00+07:00", nullable = true)
        OffsetDateTime paymentDeadlineAt,
        @Schema(description = "Luôn là true để FE không coi số tiền này là kết quả thanh toán cuối cùng.", example = "true") boolean isEstimate,
        @Schema(description = "Lưu ý hiển thị cho người dùng về tính ước tính của số tiền.", nullable = true) String disclaimer
) {
}
