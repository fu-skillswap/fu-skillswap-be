package com.fptu.exe.skillswap.modules.payment.dto.response;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Schema(description = "Kết quả checkout payment cho booking")
public record PaymentCheckoutResponse(
        UUID paymentOrderId,
        String orderCode,
        UUID bookingId,
        Integer grossScoin,
        Integer couponDiscountScoin,
        Integer campaignCreditScoin,
        Integer userCreditScoin,
        Integer remainingPayableScoin,
        PaymentOrderStatus status,
        PaymentProvider paymentProvider,
        String paymentLink,
        LocalDateTime expiresAt
) {
}
