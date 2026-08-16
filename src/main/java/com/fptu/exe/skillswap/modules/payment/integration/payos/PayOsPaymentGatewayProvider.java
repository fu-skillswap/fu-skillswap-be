package com.fptu.exe.skillswap.modules.payment.integration.payos;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentProvider;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;
import com.fptu.exe.skillswap.modules.payment.integration.PaymentGatewayProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PayOsPaymentGatewayProvider implements PaymentGatewayProvider {

    private final PayOsGateway payOsGateway;

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.PAYOS;
    }

    @Override
    public CreatePaymentLinkResult createPaymentLink(CreatePaymentLinkCommand command) {
        PayOsGateway.CreatePaymentLinkResult result = payOsGateway.createPaymentLink(
                new PayOsGateway.CreatePaymentLinkCommand(
                        command.providerOrderCode(), command.amountVnd(), command.description(), command.returnUrl(),
                        command.cancelUrl(), command.expiredAtEpochSeconds(), command.buyerName(), command.buyerEmail(),
                        command.buyerPhone(), command.items().stream()
                                .map(item -> new PayOsGateway.PaymentItem(item.name(), item.quantity(), item.priceVnd()))
                                .toList()));
        return new CreatePaymentLinkResult(result.providerOrderCode(), result.providerPaymentLinkId(),
                result.providerStatus(), result.checkoutUrl(), result.expiresAt());
    }

    @Override
    public PaymentLinkDetails getPaymentLink(long providerOrderCode) {
        PayOsGateway.PaymentLinkDetails result = payOsGateway.getPaymentLink(providerOrderCode);
        return new PaymentLinkDetails(result.providerPaymentLinkId(), result.providerStatus(), result.createdAt(), result.cancelledAt());
    }

    @Override
    public VerifiedWebhook verifyWebhook(PaymentWebhookRequest request) {
        PayOsGateway.VerifiedWebhook result = payOsGateway.verifyWebhook(request);
        return new VerifiedWebhook(result.providerOrderCode(), result.providerPaymentLinkId(), result.providerEventId(),
                result.providerTransactionId(), result.providerStatus(), result.success(), result.paidAt(), result.amount());
    }
}
