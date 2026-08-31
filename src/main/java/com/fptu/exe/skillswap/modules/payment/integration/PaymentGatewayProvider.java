package com.fptu.exe.skillswap.modules.payment.integration;

import com.fptu.exe.skillswap.shared.time.BusinessTime;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentProvider;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;

import java.time.Instant;
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
            Instant expiresAtUtc,
            LocalDateTime expiresAt
    ) {
        public CreatePaymentLinkResult(String providerOrderCode,
                                       String providerPaymentLinkId,
                                       String providerStatus,
                                       String checkoutUrl,
                                       Instant expiresAtUtc) {
            this(providerOrderCode, providerPaymentLinkId, providerStatus, checkoutUrl, expiresAtUtc,
                    expiresAtUtc != null ? BusinessTime.fromInstant(expiresAtUtc) : null);
        }

        public CreatePaymentLinkResult(String providerOrderCode,
                                       String providerPaymentLinkId,
                                       String providerStatus,
                                       String checkoutUrl,
                                       LocalDateTime expiresAt) {
            this(providerOrderCode, providerPaymentLinkId, providerStatus, checkoutUrl,
                    expiresAt != null ? BusinessTime.toInstant(expiresAt) : null, expiresAt);
        }
    }

    record PaymentLinkDetails(
            String providerPaymentLinkId,
            String providerStatus,
            Instant createdAtUtc,
            Instant cancelledAtUtc,
            LocalDateTime createdAt,
            LocalDateTime cancelledAt
    ) {
        public PaymentLinkDetails(String providerPaymentLinkId,
                                  String providerStatus,
                                  Instant createdAtUtc,
                                  Instant cancelledAtUtc) {
            this(providerPaymentLinkId, providerStatus, createdAtUtc, cancelledAtUtc,
                    createdAtUtc != null ? BusinessTime.fromInstant(createdAtUtc) : null,
                    cancelledAtUtc != null ? BusinessTime.fromInstant(cancelledAtUtc) : null);
        }

        public PaymentLinkDetails(String providerPaymentLinkId,
                                  String providerStatus,
                                  LocalDateTime createdAt,
                                  LocalDateTime cancelledAt) {
            this(providerPaymentLinkId, providerStatus,
                    createdAt != null ? BusinessTime.toInstant(createdAt) : null,
                    cancelledAt != null ? BusinessTime.toInstant(cancelledAt) : null,
                    createdAt, cancelledAt);
        }
    }

    record VerifiedWebhook(
            String providerOrderCode,
            String providerPaymentLinkId,
            String providerEventId,
            String providerTransactionId,
            String providerStatus,
            boolean success,
            Instant paidAtUtc,
            LocalDateTime paidAt,
            long amount
    ) {
        public VerifiedWebhook(String providerOrderCode,
                               String providerPaymentLinkId,
                               String providerEventId,
                               String providerTransactionId,
                               String providerStatus,
                               boolean success,
                               Instant paidAtUtc,
                               long amount) {
            this(providerOrderCode, providerPaymentLinkId, providerEventId, providerTransactionId, providerStatus,
                    success, paidAtUtc, paidAtUtc != null ? BusinessTime.fromInstant(paidAtUtc) : null, amount);
        }

        public VerifiedWebhook(String providerOrderCode,
                               String providerPaymentLinkId,
                               String providerEventId,
                               String providerTransactionId,
                               String providerStatus,
                               boolean success,
                               LocalDateTime paidAt,
                               long amount) {
            this(providerOrderCode, providerPaymentLinkId, providerEventId, providerTransactionId, providerStatus,
                    success, paidAt != null ? BusinessTime.toInstant(paidAt) : null, paidAt, amount);
        }
    }

    PaymentProvider getProvider();

    CreatePaymentLinkResult createPaymentLink(CreatePaymentLinkCommand command);

    PaymentLinkDetails getPaymentLink(long providerOrderCode);

    VerifiedWebhook verifyWebhook(PaymentWebhookRequest request);
}
