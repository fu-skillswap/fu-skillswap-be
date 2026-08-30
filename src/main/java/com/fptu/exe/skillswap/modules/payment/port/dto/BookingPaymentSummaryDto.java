package com.fptu.exe.skillswap.modules.payment.port.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record BookingPaymentSummaryDto(
        UUID paymentOrderId,
        String orderCode,
        String paymentStatus,
        String settlementStatus,
        Integer amountScoin,
        LocalDateTime paidAt,
        String checkoutUrl
) {}
