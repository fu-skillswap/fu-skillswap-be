package com.fptu.exe.skillswap.modules.payment.integration.payos;

import com.fptu.exe.skillswap.modules.booking.service.BookingTime;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;

import java.time.Instant;
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
            Instant expiresAtUtc,
            LocalDateTime expiresAt
    ) {
        public CreatePaymentLinkResult(String providerOrderCode,
                                       String providerPaymentLinkId,
                                       String providerStatus,
                                       String checkoutUrl,
                                       Instant expiresAtUtc) {
            this(providerOrderCode, providerPaymentLinkId, providerStatus, checkoutUrl, expiresAtUtc,
                    expiresAtUtc != null ? BookingTime.fromInstant(expiresAtUtc) : null);
        }

        public CreatePaymentLinkResult(String providerOrderCode,
                                       String providerPaymentLinkId,
                                       String providerStatus,
                                       String checkoutUrl,
                                       LocalDateTime expiresAt) {
            this(providerOrderCode, providerPaymentLinkId, providerStatus, checkoutUrl,
                    expiresAt != null ? BookingTime.toInstant(expiresAt) : null, expiresAt);
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
                    createdAtUtc != null ? BookingTime.fromInstant(createdAtUtc) : null,
                    cancelledAtUtc != null ? BookingTime.fromInstant(cancelledAtUtc) : null);
        }

        public PaymentLinkDetails(String providerPaymentLinkId,
                                  String providerStatus,
                                  LocalDateTime createdAt,
                                  LocalDateTime cancelledAt) {
            this(providerPaymentLinkId, providerStatus,
                    createdAt != null ? BookingTime.toInstant(createdAt) : null,
                    cancelledAt != null ? BookingTime.toInstant(cancelledAt) : null,
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
                    success, paidAtUtc, paidAtUtc != null ? BookingTime.fromInstant(paidAtUtc) : null, amount);
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
                    success, paidAt != null ? BookingTime.toInstant(paidAt) : null, paidAt, amount);
        }
    }
}
