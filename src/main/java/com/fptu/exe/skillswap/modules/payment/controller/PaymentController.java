package com.fptu.exe.skillswap.modules.payment.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutPreviewRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentWebhookRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.PaymentCheckoutPreviewResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.InternalPaymentWebhookResponse;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Payment Orders", description = "Xem trước chi phí, tạo checkout và kiểm tra trạng thái thanh toán của booking. Webhook chỉ dành cho provider.")
public class PaymentController {

    private final PaymentOrderService paymentOrderService;
    private final InMemoryRateLimitService rateLimitService;

    @Operation(summary = "Tạo payment order cho booking", description = "FE gọi sau khi mentor đã accept booking và booking có canPay=true. Backend tự áp coupon/credit, sau đó tạo Hosted Payment Link và trả checkoutUrl cho FE redirect. Gửi lại cùng Idempotency-Key khi retry do timeout.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo checkout thành công", content = @Content(examples = @ExampleObject(
            name = "Checkout chờ thanh toán",
            value = """
                    {
                      "status": 201,
                      "code": "CREATED_0201",
                      "message": "Tạo mới thành công",
                      "data": {
                        "paymentOrderId": "019f6234-aaaa-bbbb-cccc-1234567890ab",
                        "orderCode": "202609040001",
                        "bookingId": "019f5234-aaaa-bbbb-cccc-1234567890ab",
                        "attemptNo": 1,
                        "remainingPayableScoin": 110,
                        "remainingPayableVnd": 110000,
                        "status": "PENDING",
                        "checkoutUrl": "https://pay.payos.vn/web/demo-checkout",
                        "expiresAt": "2026-09-04T04:15:00+07:00",
                        "retryable": false
                      }
                    }
                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Request không hợp lệ hoặc coupon không đáp ứng điều kiện"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token hết hạn"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User không có quyền thanh toán booking này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking chưa ở trạng thái được phép thanh toán hoặc đã có payment state khác"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Booking đúng định dạng nhưng không còn đủ điều kiện nghiệp vụ để checkout")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/me/payment-orders/checkout")
    @com.fptu.exe.skillswap.shared.idempotency.Idempotent
    public ResponseEntity<ApiResponse<PaymentCheckoutResponse>> checkout(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Chỉ gửi bookingId và couponCode tùy chọn. Không gửi userId, amount hoặc provider identifier; backend tự xác định và tính lại.",
                    content = @Content(examples = @ExampleObject(
                            name = "Request tạo checkout",
                            value = """
                                    {
                                      "bookingId": "019f5234-aaaa-bbbb-cccc-1234567890ab",
                                      "couponCode": "WELCOME10"
                                    }
                                    """)))
            PaymentCheckoutRequest request) {
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

    @Operation(summary = "Xem trước chi phí thanh toán", description = "API chỉ đọc để ước tính số tiền cần thanh toán trước khi tạo checkout. Không giữ credit, không giữ coupon/ngân sách campaign và không tạo link thanh toán. FE dùng số tiền trả về để hiển thị xác nhận, sau đó gọi checkout.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trả số tiền ước tính và các khoản giảm/credit áp dụng; đây chưa phải payment order."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Booking ID hoặc dữ liệu preview không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User không có quyền xem giá của booking này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/me/bookings/{bookingId}/checkout-preview")
    public ResponseEntity<ApiResponse<PaymentCheckoutPreviewResponse>> checkoutPreview(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @RequestBody(required = false) PaymentCheckoutPreviewRequest request) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(ApiResponse.success(paymentOrderService.previewCheckout(principal.getPublicId(), bookingId, request)));
    }

    @Operation(summary = "Lấy trạng thái thanh toán", description = "API chỉ đọc trạng thái đã lưu. FE dùng đúng path /api/me/payment-orders/{targetType}/{targetId}, thường với targetType=BOOKING, sau khi người dùng quay lại từ checkout. PAID hiển thị thành công; FAILED/EXPIRED chỉ hiện nút thử lại khi retryable=true. Poll thưa; không gọi sync liên tục.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trạng thái thanh toán hiện tại; EXPIRED là trạng thái hợp lệ của phiên đã hết hạn, không phải HTTP error.", content = @Content(examples = {
            @ExampleObject(name = "Đã thanh toán", value = """
                    {
                      "status": 200,
                      "code": "SUCCESS_0200",
                      "message": "Thành công",
                      "data": {
                        "bookingId": "019f5234-aaaa-bbbb-cccc-1234567890ab",
                        "status": "PAID",
                        "expiresAt": "2026-09-04T04:15:00+07:00",
                        "userActionMessage": "Thanh toán thành công.",
                        "retryable": false
                      }
                    }
                    """),
            @ExampleObject(name = "Checkout hết hạn", value = """
                    {
                      "status": 200,
                      "code": "SUCCESS_0200",
                      "message": "Thành công",
                      "data": {
                        "bookingId": "019f5234-aaaa-bbbb-cccc-1234567890ab",
                        "status": "EXPIRED",
                        "userActionMessage": "Phiên thanh toán đã hết hạn. Bạn có thể bắt đầu lại checkout nếu booking còn cho phép.",
                        "retryable": true
                      }
                    }
                    """)
    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "targetType không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User không có quyền xem payment order này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy payment order hoặc booking")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me/payment-orders/{targetType}/{targetId}")
    public ResponseEntity<ApiResponse<PaymentCheckoutResponse>> getByTarget(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable PaymentTargetType targetType,
            @PathVariable UUID targetId) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(ApiResponse.success(paymentOrderService.getByTarget(principal.getPublicId(), targetType, targetId)));
    }

    @Operation(summary = "Đồng bộ thủ công trạng thái thanh toán", description = "Chỉ dùng khi FE cần người dùng chủ động kiểm tra lại booking đang chờ thanh toán sau khi provider callback chưa kịp cập nhật. API có giới hạn tần suất, không dùng để poll liên tục. Sau khi gọi, đọc lại status và retryable từ response.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trả trạng thái payment order sau lần đồng bộ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User không có quyền đồng bộ payment order này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy payment order hoặc booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Payment order không ở trạng thái có thể đồng bộ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Đã vượt giới hạn đồng bộ; chờ rồi thử lại theo retryAfterSeconds")
    })
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

    @Operation(tags = {"Internal/System"}, summary = "Webhook payment provider", description = "Internal/System - không dùng cho FE. PayOS gọi callback này; backend kiểm tra chữ ký và xử lý idempotent.")
    @PostMapping("/payments/webhook/payos")
    public ResponseEntity<ApiResponse<InternalPaymentWebhookResponse>> webhook(@Valid @RequestBody PaymentWebhookRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                InternalPaymentWebhookResponse.from(paymentOrderService.handleWebhook(request))));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
