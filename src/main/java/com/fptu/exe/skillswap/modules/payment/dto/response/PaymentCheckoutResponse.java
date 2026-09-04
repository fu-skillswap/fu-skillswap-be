package com.fptu.exe.skillswap.modules.payment.dto.response;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Kết quả tạo hoặc truy vấn phiên thanh toán cho booking. FE dùng checkoutUrl khi cần chuyển người dùng sang trang thanh toán.")
    public record PaymentCheckoutResponse(
        @Schema(description = "ID payment order dùng để tra cứu trạng thái.") UUID paymentOrderId,
        @Schema(description = "Mã order dùng để đối chiếu phiên thanh toán.") String orderCode,
        @Schema(description = "ID booking được thanh toán.") UUID bookingId,
        @Schema(description = "Số lần thử thanh toán của booking.", example = "1") Integer attemptNo,
        @Schema(description = "Giá dịch vụ cuối cùng theo Scoin trước khi trừ các khoản giảm/credit.") Integer priceScoin,
        @Schema(description = "Số Scoin được giảm bởi coupon.") Integer couponDiscountScoin,
        @Schema(description = "Số Scoin credit từ campaign được áp dụng.") Integer campaignCreditAppliedScoin,
        @Schema(description = "Số Scoin credit của người dùng được áp dụng.") Integer userCreditAppliedScoin,
        @Schema(description = "Số Scoin còn phải thanh toán sau các khoản giảm/credit.") Integer remainingPayableScoin,
        @Schema(description = "Số tiền quy đổi còn phải thanh toán bằng VND.") Integer remainingPayableVnd,
        @Schema(description = "Trạng thái phiên thanh toán. PAID là đã thanh toán; EXPIRED/CANCELLED/FAILED cần xử lý theo hướng dẫn của API.") PaymentOrderStatus status,
        @Schema(description = "Nhà cung cấp thanh toán hiện tại; FE chỉ dùng nếu cần hiển thị lựa chọn nhà cung cấp.") PaymentProvider paymentProvider,
        @Schema(description = "Internal field - FE không cần sử dụng. Mã order của provider.") String providerOrderCode,
        @Schema(description = "Internal field - FE không cần sử dụng. Mã payment link của provider.") String providerPaymentLinkId,
        @Schema(description = "Internal field - FE không cần sử dụng. Trạng thái thô từ provider.") String providerStatus,
        @Schema(description = "URL thanh toán tạm thời do backend tạo. FE redirect ngay khi cần; không lưu làm URL cố định.") String checkoutUrl,
        @Schema(description = "Internal/compatibility field - FE ưu tiên checkoutUrl.") String paymentLink,
        @Schema(description = "Thời điểm hết hạn thanh toán kèm offset +07:00", example = "2026-08-24T20:00:00+07:00")
        OffsetDateTime expiresAt,
        @Schema(description = "Thông báo an toàn để FE hiển thị theo trạng thái thanh toán; không chứa lỗi từ provider.", nullable = true)
        String userActionMessage,
        @Schema(description = "Cho biết người dùng có thể bắt đầu lại luồng thanh toán hay không. Không tự retry request tới provider.", example = "true")
        boolean retryable
) {
}
