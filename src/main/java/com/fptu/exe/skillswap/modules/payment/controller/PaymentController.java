package com.fptu.exe.skillswap.modules.payment.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutPreviewRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutPreviewResponse;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "Payment Orders", description = "Nhóm API checkout payment, xử lý webhook thanh toán và truy vấn trạng thái payment order theo booking.")
public class PaymentController {

    private final PaymentOrderService paymentOrderService;
    private final InMemoryRateLimitService rateLimitService;

    @Operation(summary = "Tạo payment order cho booking", description = "FE gọi sau khi booking đã sẵn sàng thanh toán. Backend tự áp coupon/credit, sau đó tạo Hosted Payment Link thật từ PayOS và trả checkoutUrl cho FE redirect.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/me/payment-orders/checkout")
    @com.fptu.exe.skillswap.shared.idempotency.Idempotent
    public ResponseEntity<ApiResponse<PaymentCheckoutResponse>> checkout(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PaymentCheckoutRequest request) {
        ensureAuthenticated(principal);
        rateLimitService.check(
                RateLimitScope.SECURITY,
                "payment:checkout:" + principal.getPublicId(),
                5,
                Duration.ofMinutes(1),
                "Bạn đang tạo checkout quá nhanh, vui lòng chờ trước khi thử lại"
        );
        PaymentCheckoutResponse response = paymentOrderService.checkout(principal.getPublicId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "Preview checkout payment", description = "Read-only estimate. It never reserves credit, coupon, campaign budget or creates a PayOS link.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/me/bookings/{bookingId}/checkout-preview")
    public ResponseEntity<ApiResponse<PaymentCheckoutPreviewResponse>> checkoutPreview(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @RequestBody(required = false) PaymentCheckoutPreviewRequest request) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(ApiResponse.success(paymentOrderService.previewCheckout(principal.getPublicId(), bookingId, request)));
    }

    @Operation(summary = "Lấy payment order theo booking", description = "API chỉ đọc trạng thái đã lưu trong database. FE có thể poll API này; webhook và reconciliation scheduler là nguồn cập nhật trạng thái chính.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me/payment-orders/{targetType}/{targetId}")
    public ResponseEntity<ApiResponse<PaymentCheckoutResponse>> getByTarget(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable PaymentTargetType targetType,
            @PathVariable UUID targetId) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(ApiResponse.success(paymentOrderService.getByTarget(principal.getPublicId(), targetType, targetId)));
    }

    @Operation(summary = "Đồng bộ thủ công payment order", description = "Chỉ dùng khi FE cần người dùng chủ động kiểm tra lại một booking đang chờ thanh toán. API gọi PayOS ngoài transaction; đã có giới hạn tần suất, không dùng để poll.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/me/payment-orders/{targetType}/{targetId}/sync")
    public ResponseEntity<ApiResponse<PaymentCheckoutResponse>> synchronizeByTarget(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable PaymentTargetType targetType,
            @PathVariable UUID targetId) {
        ensureAuthenticated(principal);
        rateLimitService.check(
                RateLimitScope.SECURITY,
                "payment:manual-sync:" + principal.getPublicId() + ":" + targetType + ":" + targetId,
                2,
                Duration.ofMinutes(1),
                "Bạn đang đồng bộ payment quá nhanh, vui lòng thử lại sau"
        );
        return ResponseEntity.ok(ApiResponse.success(
                paymentOrderService.synchronizeProviderStatus(principal.getPublicId(), targetType, targetId)));
    }

    @Operation(summary = "Webhook payment provider", description = "Endpoint nhận webhook chuẩn từ PayOS. Backend verify chữ ký thật, xử lý idempotent và chỉ chốt PAID khi webhook hợp lệ.")
    @PostMapping("/payments/webhook/payos")
    public ResponseEntity<ApiResponse<PaymentCheckoutResponse>> webhook(@Valid @RequestBody PaymentWebhookRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paymentOrderService.handleWebhook(request)));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
