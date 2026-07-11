package com.fptu.exe.skillswap.modules.payment.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.payment.dto.response.CreditWalletResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.MentorWalletResponse;
import com.fptu.exe.skillswap.modules.payment.service.WalletQueryService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
@Tag(name = "Wallet", description = "Nhóm API xem số dư ví Scoin của mentee và settlement earnings của mentor. Mỗi ví chỉ trả số dư hiện tại và 15 giao dịch gần nhất để FE render nhẹ.")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletQueryService walletQueryService;

    @Operation(
            summary = "Xem ví Scoin của tôi",
            description = "Dùng cho mentee hoặc user có nhu cầu xem số Scoin khả dụng hiện tại và 15 giao dịch gần nhất."
    )
    @GetMapping("/credit-wallet")
    @PreAuthorize("hasRole('MENTEE')")
    public ResponseEntity<ApiResponse<CreditWalletResponse>> getMyCreditWallet(
            @AuthenticationPrincipal UserPrincipal principal) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(ApiResponse.success(walletQueryService.getMyCreditWallet(principal.getPublicId())));
    }

    @Operation(
            summary = "Xem ví settlement của mentor",
            description = "Dùng cho mentor để xem số settlement earnings hiện tại và 15 giao dịch gần nhất."
    )
    @GetMapping("/mentor-wallet")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<ApiResponse<MentorWalletResponse>> getMyMentorWallet(
            @AuthenticationPrincipal UserPrincipal principal) {
        ensureAuthenticated(principal);
        return ResponseEntity.ok(ApiResponse.success(walletQueryService.getMyMentorWallet(principal.getPublicId())));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
