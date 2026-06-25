package com.fptu.exe.skillswap.modules.payment.integration.payos;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SdkPayOsGateway implements PayOsGateway {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter PAYOS_WEBHOOK_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentProperties paymentProperties;

    @Override
    public CreatePaymentLinkResult createPaymentLink(CreatePaymentLinkCommand command) {
        validateClientConfig();
        try {
            CreatePaymentLinkRequest sdkRequest = CreatePaymentLinkRequest.builder()
                    .orderCode(command.providerOrderCode())
                    .amount(command.amountVnd())
                    .description(command.description())
                    .returnUrl(command.returnUrl())
                    .cancelUrl(command.cancelUrl())
                    .expiredAt(command.expiredAtEpochSeconds())
                    .buyerName(command.buyerName())
                    .buyerEmail(command.buyerEmail())
                    .buyerPhone(command.buyerPhone())
                    .items(toItems(command.items()))
                    .signature(buildCreatePaymentSignature(command))
                    .build();

            CreatePaymentLinkResponse response = createClient().paymentRequests().create(sdkRequest);
            return new CreatePaymentLinkResult(
                    String.valueOf(response.getOrderCode()),
                    response.getPaymentLinkId(),
                    response.getStatus() == null ? null : response.getStatus().name(),
                    response.getCheckoutUrl(),
                    toLocalDateTime(response.getExpiredAt())
            );
        } catch (PayOSException ex) {
            throw new BaseException(ErrorCode.DATABASE_ERROR,
                    "Không thể tạo link thanh toán PayOS lúc này: " + safeProviderMessage(ex));
        }
    }

    @Override
    public PaymentLinkDetails getPaymentLink(long providerOrderCode) {
        validateClientConfig();
        try {
            PaymentLink paymentLink = createClient().paymentRequests().get(providerOrderCode);
            return new PaymentLinkDetails(
                    paymentLink.getId(),
                    paymentLink.getStatus() == null ? null : paymentLink.getStatus().name(),
                    parseDateTime(paymentLink.getCreatedAt()),
                    parseDateTime(paymentLink.getCanceledAt())
            );
        } catch (PayOSException ex) {
            throw new BaseException(ErrorCode.DATABASE_ERROR,
                    "Không thể đồng bộ trạng thái thanh toán PayOS: " + safeProviderMessage(ex));
        }
    }

    @Override
    public VerifiedWebhook verifyWebhook(PaymentWebhookRequest request) {
        validateClientConfig();
        try {
            WebhookData verified = createClient().webhooks().verify(toSdkWebhook(request));
            return new VerifiedWebhook(
                    verified.getOrderCode() == null ? null : String.valueOf(verified.getOrderCode()),
                    verified.getPaymentLinkId(),
                    normalizeText(verified.getReference()),
                    normalizeText(verified.getReference()),
                    normalizeText(verified.getCode()),
                    Boolean.TRUE.equals(request.success()),
                    parseWebhookPaidAt(verified.getTransactionDateTime())
            );
        } catch (PayOSException ex) {
            throw new BaseException(ErrorCode.UNAUTHORIZED,
                    "Webhook PayOS không hợp lệ hoặc sai chữ ký");
        }
    }

    private PayOS createClient() {
        PaymentProperties.PayosProperties payos = paymentProperties.getPayos();
        return new PayOS(
                payos.getClientId(),
                payos.getApiKey(),
                payos.effectiveChecksumKey()
        );
    }

    private void validateClientConfig() {
        PaymentProperties.PayosProperties payos = paymentProperties.getPayos();
        if (!StringUtils.hasText(payos.getClientId())
                || !StringUtils.hasText(payos.getApiKey())
                || !StringUtils.hasText(payos.effectiveChecksumKey())) {
            throw new BaseException(
                    ErrorCode.CONFIGURATION_ERROR,
                    "PayOS chưa được cấu hình đầy đủ. Hãy khai báo PAYOS_CLIENT_ID, PAYOS_API_KEY và PAYOS_CHECKSUM_KEY"
            );
        }
        if (!StringUtils.hasText(payos.getReturnUrl()) || !StringUtils.hasText(payos.getCancelUrl())) {
            throw new BaseException(
                    ErrorCode.CONFIGURATION_ERROR,
                    "PayOS chưa có returnUrl/cancelUrl hợp lệ. Hãy khai báo PAYOS_RETURN_URL và PAYOS_CANCEL_URL"
            );
        }
    }

    private List<PaymentLinkItem> toItems(List<PaymentItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of(PaymentLinkItem.builder()
                    .name("SkillSwap mentoring session")
                    .quantity(1)
                    .price(0L)
                    .build());
        }
        return items.stream()
                .filter(Objects::nonNull)
                .map(item -> PaymentLinkItem.builder()
                        .name(item.name())
                        .quantity(Math.max(1, item.quantity()))
                        .price(Math.max(0L, item.priceVnd()))
                        .build())
                .toList();
    }

    private String buildCreatePaymentSignature(CreatePaymentLinkCommand command) {
        Map<String, String> payload = Map.of(
                "amount", String.valueOf(command.amountVnd()),
                "cancelUrl", command.cancelUrl(),
                "description", command.description(),
                "orderCode", String.valueOf(command.providerOrderCode()),
                "returnUrl", command.returnUrl()
        );
        String data = payload.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        return hmacSha256Hex(data, paymentProperties.getPayos().effectiveChecksumKey());
    }

    private String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR,
                    "Không thể ký request tạo link thanh toán PayOS");
        }
    }

    private Webhook toSdkWebhook(PaymentWebhookRequest request) {
        PaymentWebhookRequest.PaymentWebhookDataRequest data = request.data();
        return Webhook.builder()
                .code(request.code())
                .desc(request.desc())
                .success(request.success())
                .signature(request.signature())
                .data(WebhookData.builder()
                        .orderCode(data.orderCode())
                        .amount(data.amount())
                        .description(data.description())
                        .accountNumber(data.accountNumber())
                        .reference(data.reference())
                        .transactionDateTime(data.transactionDateTime())
                        .currency(data.currency())
                        .paymentLinkId(data.paymentLinkId())
                        .code(data.code())
                        .desc(data.desc())
                        .counterAccountBankId(data.counterAccountBankId())
                        .counterAccountBankName(data.counterAccountBankName())
                        .counterAccountName(data.counterAccountName())
                        .counterAccountNumber(data.counterAccountNumber())
                        .virtualAccountName(data.virtualAccountName())
                        .virtualAccountNumber(data.virtualAccountNumber())
                        .build())
                .build();
    }

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), VIETNAM_ZONE);
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(value, PAYOS_WEBHOOK_TIME_FORMAT);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private LocalDateTime parseWebhookPaidAt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, PAYOS_WEBHOOK_TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            return parseDateTime(value);
        }
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String safeProviderMessage(PayOSException ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return "PayOS không trả về thông tin chi tiết";
        }
        return message.toLowerCase(Locale.ROOT).contains("null")
                ? "PayOS từ chối request hoặc không phản hồi chi tiết"
                : message;
    }
}
