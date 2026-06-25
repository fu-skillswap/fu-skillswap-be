package com.fptu.exe.skillswap.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "application.payment")
public class PaymentProperties {

    /**
     * Platform commission in basis points.
     * 1000 bps = 10%.
     */
    private int platformCommissionBps = 1000;

    /**
     * Payment link expiry in minutes.
     */
    private int paymentLinkExpiryMinutes = 30;

    private PayosProperties payos = new PayosProperties();

    @Getter
    @Setter
    public static class PayosProperties {
        private String checkoutBaseUrl = "https://payos.example/checkout";
        private String returnUrl = "http://localhost:3000/payment/return";
        private String cancelUrl = "http://localhost:3000/payment/cancel";
        private String webhookSecret = "";
    }
}
