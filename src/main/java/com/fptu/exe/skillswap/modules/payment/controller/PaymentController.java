package com.fptu.exe.skillswap.modules.payment.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "Payment Orders", description = "Nhóm API checkout payment, xử lý webhook thanh toán và truy vấn trạng thái payment order theo booking.")
public class PaymentController {

    private final PaymentOrderService paymentOrderService;

    @Operation(summary = "Tạo payment order cho booking", description = "FE gọi sau khi booking đã sẵn sàng thanh toán. Backend tự áp coupon/credit, sau đó tạo Hosted Payment Link thật từ PayOS và trả checkoutUrl cho FE redirect.")
    @PostMapping("/me/payment-orders/checkout")
    public ResponseEntity<ApiResponse<PaymentCheckoutResponse>> checkout(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PaymentCheckoutRequest request) {
        ensureAuthenticated(principal);
        PaymentCheckoutResponse response = paymentOrderService.checkout(principal.getPublicId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "Lấy payment order theo booking", description = "FE dùng để poll trạng thái payment order theo booking. Webhook PayOS mới là nguồn chốt PAID; endpoint này chỉ trả trạng thái backend hiện tại và đồng bộ soft status từ provider nếu cần.")
    @GetMapping("/me/payment-orders/{bookingId}")
    public ResponseEntity<ApiResponse<PaymentCheckoutResponse>> getByBookingId(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(ApiResponse.success(paymentOrderService.getByBookingId(principal.getPublicId(), bookingId)));
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
