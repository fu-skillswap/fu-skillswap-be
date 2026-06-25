package com.fptu.exe.skillswap.modules.payment.integration.payos;

import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;

import java.time.LocalDateTime;
import java.util.List;

public interface PayOsGateway {

    CreatePaymentLinkResult createPaymentLink(CreatePaymentLinkCommand command);

    PaymentLinkDetails getPaymentLink(long providerOrderCode);

    VerifiedWebhook verifyWebhook(PaymentWebhookRequest request);

    record CreatePaymentLinkCommand(
            long providerOrderCode,
            long amountVnd,
            String description,
            String returnUrl,
            String cancelUrl,
            long expiredAtEpochSeconds,
            String buyerName,
            String buyerEmail,
            String buyerPhone,
            List<PaymentItem> items
    ) {
    }

    record PaymentItem(
            String name,
            int quantity,
            long priceVnd
    ) {
    }

    record CreatePaymentLinkResult(
            String providerOrderCode,
            String providerPaymentLinkId,
            String providerStatus,
            String checkoutUrl,
            LocalDateTime expiresAt
    ) {
    }

    record PaymentLinkDetails(
            String providerPaymentLinkId,
            String providerStatus,
            LocalDateTime createdAt,
            LocalDateTime cancelledAt
    ) {
    }

    record VerifiedWebhook(
            String providerOrderCode,
            String providerPaymentLinkId,
            String providerEventId,
            String providerTransactionId,
            String providerStatus,
            boolean success,
            LocalDateTime paidAt
    ) {
    }
}
