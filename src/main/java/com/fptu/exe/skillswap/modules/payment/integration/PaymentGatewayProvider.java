package com.fptu.exe.skillswap.modules.payment.integration;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentProvider;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentGatewayProvider {

    record PaymentItem(String name, int quantity, long priceVnd) {
    }

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
            LocalDateTime paidAt,
            long amount
    ) {
    }

    PaymentProvider getProvider();

    CreatePaymentLinkResult createPaymentLink(CreatePaymentLinkCommand command);

    PaymentLinkDetails getPaymentLink(long providerOrderCode);

    VerifiedWebhook verifyWebhook(PaymentWebhookRequest request);
}
