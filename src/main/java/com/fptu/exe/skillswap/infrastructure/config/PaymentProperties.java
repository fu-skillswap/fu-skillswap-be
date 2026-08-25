package com.fptu.exe.skillswap.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "application.payment")
@Validated
public class PaymentProperties {

    /**
     * Fallback only for payment orders created before fee snapshots were introduced.
     * New booking orders derive platform revenue from the two rates below and persist
     * the exact monetary amounts on the order.
     */
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

    /** Explicit conversion used when building PayOS VND amounts. */
    @Min(1)
    @Max(1_000_000)
    private int scoinToVndRate = 1;

    /**
     * Phí nền tảng trừ từ giá dịch vụ của mentor, tính theo basis point.
     * Mặc định 500 bps = 5%.
     */
    @Min(0)
    @Max(10_000)
    private int mentorCommissionBps = 500;

    /** Phí cộng thêm cho học viên khi mua khóa học, tách riêng để có thể thay đổi sau này. */
    @Min(0)
    @Max(10_000)
    private int courseBuyerFeeBps = 1000;

    /** Phí nền tảng trừ từ doanh thu khóa học của mentor. */
    @Min(0)
    @Max(10_000)
    private int courseMentorCommissionBps = 500;

    /** Mentoring-service durations selectable by a mentor. */
    @NotEmpty
    private List<@NotNull @Min(1) Integer> allowedServiceDurationsMinutes = List.of(30, 60, 90, 120);

    /** Price bounds are kept in configuration so product policy changes require no code edit. */
    @Min(1)
    private int minPriceScoinPerMinute = 500;

    @Min(1)
    private int maxPriceScoinPerMinute = 500_000;

    @Valid
    private LateMenteeCancellationProperties lateMenteeCancellation = new LateMenteeCancellationProperties();

    /**
     * Thời gian hết hạn của link thanh toán, tính bằng phút.
     */
    @Min(1)
    private int paymentLinkExpiryMinutes = 30;

    /** Guardrails for the background provider reconciliation worker. */
    @Min(1)
    @Max(50)
    private int reconciliationMaxOrdersPerRun = 50;

    @Min(1)
    @Max(300)
    private int reconciliationMaxDurationSeconds = 45;

    @AssertTrue(message = "Tổng tỷ lệ hủy sát giờ của mentee phải bằng 10000 bps")
    public boolean isLateMenteeCancellationAllocationValid() {
        if (lateMenteeCancellation == null) {
            return false;
        }
        long total = (long) lateMenteeCancellation.getRefundBps()
                + lateMenteeCancellation.getMentorBps()
                + lateMenteeCancellation.getPlatformBps();
        return total == 10_000L;
    }

    /**
     * Flat accessors keep payment services independent from the nested configuration type.
     * This also avoids exposing an infrastructure implementation detail across Modulith boundaries.
     */
    public int getLateMenteeCancellationRefundBps() {
        return lateMenteeCancellation.getRefundBps();
    }

    public int getLateMenteeCancellationMentorBps() {
        return lateMenteeCancellation.getMentorBps();
    }

    public int getLateMenteeCancellationPlatformBps() {
        return lateMenteeCancellation.getPlatformBps();
    }

    @Getter
    @Setter
    public static class LateMenteeCancellationProperties {
        @Min(0)
        @Max(10_000)
        private int refundBps = 5000;

        @Min(0)
        @Max(10_000)
        private int mentorBps = 3500;

        @Min(0)
        @Max(10_000)
        private int platformBps = 1500;
    }

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
