package com.fptu.exe.skillswap.modules.payment.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.payment.dto.request.AdminPayoutRequestListRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.AdminNoteRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PayoutRequestCreateRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PayoutRequestResponse;
import com.fptu.exe.skillswap.modules.payment.service.PayoutService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "Payout Requests", description = "Nhóm API mentor tạo payout request và admin duyệt/ghi nhận chi trả thủ công.")
@SecurityRequirement(name = "bearerAuth")
public class PayoutController {

    private final PayoutService payoutService;

    @Operation(summary = "Tạo payout request", description = "Mentor tạo yêu cầu rút settlement balance đang có.")
    @PreAuthorize("hasRole('MENTOR')")
    @PostMapping("/mentor/payout-requests")
    @com.fptu.exe.skillswap.shared.idempotency.Idempotent
    public ResponseEntity<ApiResponse<PayoutRequestResponse>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PayoutRequestCreateRequest request) {
        ensureAuthenticated(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(payoutService.createRequest(principal.getPublicId(), request)));
    }

    @Operation(summary = "Lấy payout requests của mentor", description = "Mentor xem lịch sử payout request của mình.")
    @PreAuthorize("hasRole('MENTOR')")
    @GetMapping("/mentor/payout-requests")
    public ResponseEntity<ApiResponse<List<PayoutRequestResponse>>> listMine(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(ApiResponse.success(payoutService.getByMentor(principal.getPublicId())));
    }

    @Operation(summary = "Lấy danh sách payout requests cho admin", description = "Admin xem queue payout requests toàn hệ thống để duyệt và theo dõi chi trả.")
    @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
    @GetMapping("/admin/payout-requests")
    public ResponseEntity<ApiResponse<PageResponse<PayoutRequestResponse>>> listForAdmin(
            @ParameterObject @ModelAttribute AdminPayoutRequestListRequest request) {
        return ResponseEntity.ok(ApiResponse.success(payoutService.getAdminPayoutRequests(request)));
    }

    @Operation(summary = "Lấy chi tiết một payout request cho admin", description = "Admin xem đầy đủ snapshot và timeline của một payout request.")
    @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
    @GetMapping("/admin/payout-requests/{payoutRequestId}")
    public ResponseEntity<ApiResponse<PayoutRequestResponse>> getDetailForAdmin(@PathVariable UUID payoutRequestId) {
        return ResponseEntity.ok(ApiResponse.success(payoutService.getAdminPayoutRequestDetail(payoutRequestId)));
    }

    @Operation(summary = "Duyệt payout request", description = "Admin duyệt payout request thủ công.")
    @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
    @PostMapping("/admin/payout-requests/{payoutRequestId}/approve")
    public ResponseEntity<ApiResponse<PayoutRequestResponse>> approve(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID payoutRequestId,
            @RequestBody(required = false) AdminNoteRequest request) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(ApiResponse.success(payoutService.approve(principal.getPublicId(), payoutRequestId, request == null ? null : request.note())));
    }

    @Operation(summary = "Từ chối payout request", description = "Admin từ chối payout request thủ công.")
    @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
    @PostMapping("/admin/payout-requests/{payoutRequestId}/reject")
    public ResponseEntity<ApiResponse<PayoutRequestResponse>> reject(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID payoutRequestId,
            @RequestBody(required = false) AdminNoteRequest request) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(ApiResponse.success(payoutService.reject(principal.getPublicId(), payoutRequestId, request == null ? null : request.note())));
    }

    @Operation(summary = "Đánh dấu payout đã chi trả", description = "Admin mark payout request đã được chi trả thủ công.")
    @PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
    @PostMapping("/admin/payout-requests/{payoutRequestId}/mark-paid")
    public ResponseEntity<ApiResponse<PayoutRequestResponse>> markPaid(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID payoutRequestId,
            @RequestBody(required = false) AdminNoteRequest request) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(ApiResponse.success(payoutService.markPaid(principal.getPublicId(), payoutRequestId, request == null ? null : request.note())));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
