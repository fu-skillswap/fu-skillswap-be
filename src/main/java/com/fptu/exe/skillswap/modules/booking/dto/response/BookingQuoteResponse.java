package com.fptu.exe.skillswap.modules.booking.dto.response;

import com.fptu.exe.skillswap.modules.payment.dto.response.ServicePricingPreviewResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Read-only quote for a currently selectable booking candidate")
public record BookingQuoteResponse(
        UUID slotId,
        UUID serviceId,
        String serviceTitle,
        Integer durationMinutes,
        LocalDateTime scheduledStartAt,
        LocalDateTime scheduledEndAt,
        LocalDateTime pendingExpireAt,
        int paymentWindowMinutes,
        int paymentPreparationBufferMinutes,
        ServicePricingPreviewResponse pricing,
        BookingCancellationRefundPolicyResponse cancellationRefundPolicy,
        boolean isEstimate,
        String disclaimer
) {
}
