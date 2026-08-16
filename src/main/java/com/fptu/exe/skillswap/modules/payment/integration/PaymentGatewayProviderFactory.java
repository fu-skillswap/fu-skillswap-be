package com.fptu.exe.skillswap.modules.payment.integration;

import com.fptu.exe.skillswap.modules.payment.domain.PaymentProvider;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PaymentGatewayProviderFactory {

    private final Map<PaymentProvider, PaymentGatewayProvider> providers = new EnumMap<>(PaymentProvider.class);

    public PaymentGatewayProviderFactory(List<PaymentGatewayProvider> providerList) {
        for (PaymentGatewayProvider provider : providerList) {
            providers.put(provider.getProvider(), provider);
        }
    }

    public PaymentGatewayProvider getProvider(PaymentProvider provider) {
        PaymentProvider target = provider != null ? provider : PaymentProvider.PAYOS;
        return Optional.ofNullable(providers.get(target))
                .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Cổng thanh toán không được hỗ trợ: " + target));
    }
}
