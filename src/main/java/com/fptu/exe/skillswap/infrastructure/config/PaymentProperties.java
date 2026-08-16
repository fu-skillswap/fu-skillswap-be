package com.fptu.exe.skillswap.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@ConfigurationProperties(prefix = "application.payment")
@Validated
public class PaymentProperties {

    @Min(0)
    @Max(10_000)
    private int platformCommissionBps = 1000;

    /**
     * Phí cộng thêm cho mentee, tính theo basis point.
     * 1000 bps = 10%.
     */
    @Min(0)
    @Max(10_000)
    private int menteeSurchargeBps = 1000;

    /**
     * Phí nền tảng của mentor, tính theo basis point.
     * 1000 bps = 10%.
     */
    @Min(0)
    @Max(10_000)
    private int mentorCommissionBps = 1000;

    /**
     * Thời gian hết hạn của link thanh toán, tính bằng phút.
     */
    @Min(1)
    private int paymentLinkExpiryMinutes = 30;

    @Valid
    private PayosProperties payos = new PayosProperties();

    @Getter
    @Setter
    public static class PayosProperties {
        private String clientId = "";
        private String apiKey = "";
        private String checksumKey = "";
        @NotBlank
        private String returnUrl = "http://localhost:3000/payment/return";
        @NotBlank
        private String cancelUrl = "http://localhost:3000/payment/cancel";
        private String webhookUrl = "";
        /**
         * Tên cũ để tương thích cấu hình local trước đây.
         * Bản deploy mới nên dùng checksumKey.
         */
        private String webhookSecret = "";

        public String effectiveChecksumKey() {
            if (checksumKey != null && !checksumKey.isBlank()) {
                return checksumKey;
            }
            return webhookSecret;
        }
    }
}
